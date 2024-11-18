/**
 * Copyright 2021 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import { v4 as uuidv4 } from 'uuid';

import * as firebase from 'firebase-admin';
import * as functions from 'firebase-functions';

import { DATABASE as SnoozeNotificationsDatabase } from '../../database/SnoozeNotificationsDatabase';
import { DATABASE as SensorEventDatabase } from '../../database/SensorEventDatabase';

import { Config } from '../../database/ServerConfigDatabase';
import { isAuthorizedToPushRemoteButton } from '../../controller/Auth';
import { SnoozeRequest } from '../../model/SnoozeRequest';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';
const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EMAIL_PARAM_KEY = "email";
const SNOOZE_DURATION_PARAM_KEY = 'snoozeDuration';
const SNOOZE_EVENT_TIMESTAMP_KEY = 'snoozeEventTimestamp';
const VALID_SNOOZE_DURATIONS: Array<String> = ['0h', '1h', '2h', '3h', '4h', '5h', '6h', '7h', '8h', '9h', '10h', '11h', '12h'];
/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/remoteButton?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const httpSnoozeNotificationsRequest = functions.https.onRequest(async (request, response) => {
    const config = await Config.get();
    if (!Config.isSnoozeNotificationsEnabled(config)) {
        response.status(400).send({ error: 'Disabled' });
        return;
    }
    if (request.method !== 'POST') {
        response.status(405).send({ error: 'Method Not Allowed.' });
        return;
    }

    // Echo query parameters and body.
    const data = {
        queryParams: request.query,
        body: request.body
    };

    // Borrow the same logic to verify if the user is allowed to push the button.
    // Use this logic to verify if a user can snooze notifications for everyone.
    const buttonPushKeyHeader = request.get('X-RemoteButtonPushKey');
    if (!buttonPushKeyHeader || buttonPushKeyHeader.length <= 0) {
        const result = { error: 'Unauthorized (key).' };
        console.error(result);
        response.status(401).send(result);
        return;
    }
    if (Config.getRemoteButtonPushKey(config) !== buttonPushKeyHeader) {
        const result = { error: 'Forbidden (key).' };
        console.error(result);
        response.status(403).send(result);
        return;
    }
    // Use Firebase Auth ID Token to authenticate the user.
    const googleIdToken = request.get('X-AuthTokenGoogle');
    console.log('googleIdToken:', googleIdToken);
    if (!googleIdToken || googleIdToken.length <= 0) {
        const result = { error: 'Unauthorized (token).' };
        console.error(result);
        response.status(401).send(result);
        return;
    }
    let verifiedEmail = null;
    try {
        const decodedToken = await firebase.auth().verifyIdToken(googleIdToken);
        verifiedEmail = decodedToken.email;
    } catch (error: any) {
        console.error(error);
        const result = { error: 'Unauthorized (token).' };
        response.status(401).send(result);
        return;
    }
    const email = verifiedEmail;
    console.log('email:', email);
    // Check to make sure the user is authorized to push the remote button.
    const authorizedEmails = Config.getRemoteButtonAuthorizedEmails(config);
    if (!isAuthorizedToPushRemoteButton(email, authorizedEmails)) {
        const result = { error: 'Forbidden (user).' };
        console.error(result);
        response.status(403).send(result);
        return;
    }
    // Put the user email in the result.
    data[EMAIL_PARAM_KEY] = email;

    // The session ID allows a client to tell the server that multiple requests
    // come from the same session.
    if (SESSION_PARAM_KEY in request.query) {
        // If the client sends a session ID, respond with the session ID.
        data[SESSION_PARAM_KEY] = request.query[SESSION_PARAM_KEY];
    } else {
        // If the client does not send a session ID, create a session ID.
        data[SESSION_PARAM_KEY] = uuidv4();
    }

    // This is where the snooze notifications logic is implemented.

    // Get the build timestamp from the request.
    // The build timestamp is unique to each device.
    if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
        data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
    } else {
        // TODO: Determine if we should abort the request at this point.
        // I think a build timestamp should be required.
        console.error('No build timestamp in request');
        const result = { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];

    // Get the current event timestamp from the database.
    let eventsCurrent = null;
    try {
        eventsCurrent = await SensorEventDatabase.get(buildTimestamp);
    } catch (error) {
        console.error(error);
        const result = { error: 'Error getting current event' };
        response.status(500).send(result);
        return;
    }
    if (!eventsCurrent) {
        console.error('No current event');
        const result = { error: 'No current event' };
        response.status(400).send(result);
        return;
    }
    if (!eventsCurrent.currentEvent || !eventsCurrent.currentEvent.timestampSeconds) {
        console.error('No current event timestamp');
        const result = { error: 'No current event timestamp' };
        response.status(400).send(result);
        return;
    }
    const currentEventTimestampSeconds = parseInt(eventsCurrent.currentEvent.timestampSeconds);

    // The request is only valid if the snooze event timestamp matches the current event.
    if (SNOOZE_EVENT_TIMESTAMP_KEY in request.query) {
        data[SNOOZE_EVENT_TIMESTAMP_KEY] = request.query[SNOOZE_EVENT_TIMESTAMP_KEY];
    } else {
        console.error('No snooze event timestamp in request');
        const result = { error: 'Missing required parameter: ' + SNOOZE_EVENT_TIMESTAMP_KEY };
        response.status(400).send(result);
        return;
    }
    if (currentEventTimestampSeconds !== parseInt(data[SNOOZE_EVENT_TIMESTAMP_KEY])) {
        console.log('currentEventTimestampSeconds:', currentEventTimestampSeconds);
        console.log('data[SNOOZE_EVENT_TIMESTAMP_KEY]:', data[SNOOZE_EVENT_TIMESTAMP_KEY]);
        console.error('Snooze event timestamp does not match current event timestamp');
        const result = { error: 'Snooze event timestamp does not match current event timestamp' };
        response.status(400).send(result);
        return;
    }

    // Get the snooze duration from the request.
    // This is a String from an enumerated list of possoble values.
    if (SNOOZE_DURATION_PARAM_KEY in request.query) {
        const snoozeDurationParam = request.query[SNOOZE_DURATION_PARAM_KEY] as String;
        if (VALID_SNOOZE_DURATIONS.includes(snoozeDurationParam)) {
            data[SNOOZE_DURATION_PARAM_KEY] = snoozeDurationParam;
        }
    } else {
        console.error('No snooze duration in request');
        const result = { error: 'Missing required parameter: ' + SNOOZE_DURATION_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    if (!(SNOOZE_DURATION_PARAM_KEY in data)) {
        console.error('Invalid snooze duration in request, must be one of:', VALID_SNOOZE_DURATIONS);
        const result = { error: 'Invalid snooze duration' };
        response.status(400).send(result);
        return;
    }
    const snoozeDurationData = data[SNOOZE_DURATION_PARAM_KEY] ?? null;
    const snoozeDuration: string | null =
        typeof snoozeDurationData === 'string' ? snoozeDurationData : null;

    if (!snoozeDuration) {
        console.error('Missing snooze duration in request');
        const result = { error: 'Missing required parameter: ' + SNOOZE_DURATION_PARAM_KEY };
        response.status(400).send(result);
        return;
    }

    // Calculate the snooze end time from the current time.
    let durationSeconds: number | null = null;
    if (snoozeDuration && snoozeDuration.endsWith('h')) {
        const durationHours = parseInt(snoozeDuration, 10);
        // Support 0h to 12h snooze durations.
        if (durationHours >= 0 && durationHours <= 12) {
            durationSeconds = durationHours * 60 * 60;
        } else {
            console.error("Invalid snooze duration:", snoozeDuration);
        }
    } else {
        console.error("Invalid snooze duration:", snoozeDuration);
    }
    if (durationSeconds === null || typeof durationSeconds === 'number') {
        console.error('Snooze duration is invalid:', snoozeDuration);
        const result = { error: 'Invalid parameter ' + SNOOZE_DURATION_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    const nowSeconds: number = firebase.firestore.Timestamp.now().seconds;
    const snoozeEndTimeSeconds: number = nowSeconds + durationSeconds;

    // Save the snooze data to the database.
    const snoozeData: SnoozeRequest = <SnoozeRequest>{
        currentEventTimestampSeconds: currentEventTimestampSeconds,
        snoozeRequestSeconds: nowSeconds,
        snoozeDuration: snoozeDuration ?? '',
        snoozeEndTimeSeconds: snoozeEndTimeSeconds,
    };
    try {
        await SnoozeNotificationsDatabase.set(buildTimestamp, snoozeData);
        const snoozeResult = await SnoozeNotificationsDatabase.get(buildTimestamp);
        response.status(200).send(snoozeResult);
    } catch (error) {
        console.error(error);
        response.status(500).send(error);
    }
});

/**
 * Get latest Snooze request.
 */
export const httpSnoozeNotificationsLatest = functions.https.onRequest(async (request, response) => {
    const config = await Config.get();
    if (!Config.isSnoozeNotificationsEnabled(config)) {
        response.status(400).send({ error: 'Disabled' });
        return;
    }
    if (request.method !== 'GET') {
        response.status(405).send({ error: 'Method Not Allowed.' });
        return;
    }
    // Echo query parameters and body.
    const data = {
        queryParams: request.query,
        body: request.body
    };
    // Get the build timestamp from the request.
    // The build timestamp is unique to each device.
    if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
        data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
    } else {
        // TODO: Determine if we should abort the request at this point.
        // I think a build timestamp should be required.
        console.error('No build timestamp in request');
        const result = { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
    const nowSeconds = firebase.firestore.Timestamp.now().seconds;
    try {
        const snoozeResult = await SnoozeNotificationsDatabase.get(buildTimestamp);
        if (!snoozeResult || !snoozeResult.snoozeEndTimeSeconds) {
            response.status(200).send({ status: 'NONE' });
            return
        }
        console.log(snoozeResult);
        if (nowSeconds > snoozeResult.snoozeEndTimeSeconds) {
            response.status(200).send({ status: 'EXPIRED', snooze: snoozeResult });
            return;
        }
        response.status(200).send({ status: 'ACTIVE', snooze: snoozeResult });
        return;
    } catch (error) {
        console.error(error)
        response.status(500).send(error)
        return;
    }
});
