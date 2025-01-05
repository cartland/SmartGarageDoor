/**
 * Copyright 2024 Chris Cartland. All Rights Reserved.
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

import * as firebase from 'firebase-admin';
import * as functions from 'firebase-functions';

import { Config } from '../../database/ServerConfigDatabase';
import { isAuthorizedToPushRemoteButton } from '../../controller/Auth';
import { getSnoozeStatus, SnoozeLatestParams, SnoozeLatestResponse, SubmitSnoozeParams, submitSnoozeNotificationsRequest, SubmitSnoozeResponse } from '../../controller/SnoozeNotifications';

const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const SNOOZE_DURATION_PARAM_KEY = 'snoozeDuration';
const SNOOZE_EVENT_TIMESTAMP_KEY = 'snoozeEventTimestamp';

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/snoozeNotificationsLatest?buildTimestamp=buildTimestamp
 */
export const httpSnoozeNotificationsRequest = functions.https.onRequest(async (request, response) => {
    // Handle HTTP request.
    // * Check if snooze notifications are enabled.
    // * Check that the request is a POST request.
    // * Get the headers from the request.
    //   * X-RemoteButtonPushKey
    //     * Check if the key is authorized to push the remote button.
    //   * X-AuthTokenGoogle
    //     * Check if the user is authorized to push the remote button.
    // * Get the parameters from the request.
    //     * buildTimestamp
    //     * snoozeDuration
    //     * snoozeEventTimestamp
    // Then implement the logic to snooze notifications.
    // * Check if the snoozeDuration is valid.
    // * Check if the snoozeEventTimestamp matches the current event.
    // * Return the JSON response:
    //     * SnoozeRequest | error: string

    // Handle the HTTP request.
    const config = await Config.get();
    if (!Config.isSnoozeNotificationsEnabled(config)) {
        response.status(400).send({ error: 'Disabled' });
        return;
    }
    if (request.method !== 'POST') {
        response.status(405).send({ error: 'Method Not Allowed.' });
        return;
    }

    // * Get the headers from the request.

    // Check if the key is authorized to push the remote button.
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

    // Check if the user is authorized to push the remote button.
    // Use Firebase Auth ID Token to authenticate the user.
    const googleIdToken = request.get('X-AuthTokenGoogle');
    console.log('googleIdToken:', googleIdToken);
    if (!googleIdToken || googleIdToken.length <= 0) {
        const result = { error: 'Unauthorized (token).' };
        console.error(result);
        response.status(401).send(result);
        return;
    }
    let email = null;
    try {
        const decodedToken = await firebase.auth().verifyIdToken(googleIdToken);
        email = decodedToken.email;
    } catch (error: any) {
        console.error(error);
        const result = { error: 'Unauthorized (token).' };
        response.status(401).send(result);
        return;
    }
    console.log('email:', email);
    // User is authenticated. Check if they are authorized.
    const authorizedEmails = Config.getRemoteButtonAuthorizedEmails(config);
    if (!isAuthorizedToPushRemoteButton(email, authorizedEmails)) {
        const result = { error: 'Forbidden (user).' };
        console.error(result);
        response.status(403).send(result);
        return;
    }

    // * Get the parameters from the request.
    let params: SubmitSnoozeParams = null;
    try {
        params = <SubmitSnoozeParams>{
            buildTimestamp: request.query[BUILD_TIMESTAMP_PARAM_KEY] as string,
            snoozeDuration: request.query[SNOOZE_DURATION_PARAM_KEY] as string,
            snoozeEventTimestamp: request.query[SNOOZE_EVENT_TIMESTAMP_KEY] as string,
        };
    } catch (error) {
        const result = {
            error: 'Could not parse parameters: '
                + BUILD_TIMESTAMP_PARAM_KEY + ', ' + SNOOZE_DURATION_PARAM_KEY + ', ' + SNOOZE_EVENT_TIMESTAMP_KEY
        };
        console.error(result.error);
        response.status(400).send(result);
        return;
    }
    if (!params.buildTimestamp) {
        console.error('No build timestamp in request');
        const result = { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    if (!params.snoozeDuration) {
        console.error('No snooze duration in request');
        const result = { error: 'Missing required parameter: ' + SNOOZE_DURATION_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    if (!params.snoozeEventTimestamp) {
        console.error('No snooze event timestamp in request');
        const result = { error: 'Missing required parameter: ' + SNOOZE_EVENT_TIMESTAMP_KEY };
        response.status(400).send(result);
        return;
    }

    const snoozeResponse: SubmitSnoozeResponse = await submitSnoozeNotificationsRequest(params);

    // Return the HTTP response.
    if (snoozeResponse.error) {
        console.error('Returning HTTP 500 error');
        response.status(snoozeResponse.code ?? 500).send(snoozeResponse);
        return;
    }
    const snooze = snoozeResponse.snooze
    console.info('Returning HTTP 200 success:', snooze);
    response.status(200).send(snooze);
});

/**
 * Get latest Snooze request.
 *
 * curl -X GET \
 *    -H "Content-Type: application/json" \
 *    -d '{}' \ "http://localhost:5000/PROJECT-ID/us-central1/snoozeNotificationsLatest?buildTimestamp=Sat%20Mar%2013%2014%3A45%3A00%202021"
 */
export const httpSnoozeNotificationsLatest = functions.https.onRequest(async (request, response) => {
    // Handle HTTP request.
    // * Check if snooze notifications are enabled.
    // * Check that the request is a GET request.
    // * Get the parameters from the request.
    //     * buildTimestamp
    // Then implement the logic to get the latest snooze request.
    // * Get the current event timestamp from the database.
    // * Get the latest snooze request from the database.
    // * Check if the snooze request is active, expired, or none.
    //     * Result: ACTIVE, EXPIRED, NONE
    // * Return the JSON response:
    //     * status: string = ACTIVE, EXPIRED, NONE
    //     * snooze: SnoozeRequest
    //     * error: string

    // Handle the HTTP request.
    const config = await Config.get();
    if (!Config.isSnoozeNotificationsEnabled(config)) {
        response.status(400).send({ error: 'Disabled' });
        return;
    }
    if (request.method !== 'GET') {
        response.status(405).send({ error: 'Method Not Allowed.' });
        return;
    }
    let params: SnoozeLatestParams = null;
    try {
        params = <SnoozeLatestParams>{
            buildTimestamp: request.query[BUILD_TIMESTAMP_PARAM_KEY] as string,
        };
    } catch (error) {
        console.error('No build timestamp in request');
        const result = { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY };
        response.status(400).send(result);
        return;
    }
    if (!params.buildTimestamp) {
        console.error('No build timestamp in request');
        const result = { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY };
        response.status(400).send(result);
        return;
    }

    // Implement the core logic.
    const snoozeResponse: SnoozeLatestResponse = await getSnoozeStatus(params);

    // Return the HTTP response.
    if (snoozeResponse.error) {
        console.error('Returning HTTP 500 error');
        response.status(500).send(snoozeResponse);
        return;
    }
    console.info('Returning HTTP 200 success:', snoozeResponse);
    response.status(200).send(snoozeResponse);
});
