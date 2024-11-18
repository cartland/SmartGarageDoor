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

import { DATABASE as SnoozeNotificationsDatabase } from '../database/SnoozeNotificationsDatabase';
import { DATABASE as SensorEventDatabase } from '../database/SensorEventDatabase';

import { SnoozeRequest, SnoozeStatus } from '../model/SnoozeRequest';

const SNOOZE_DURATION_PARAM_KEY = 'snoozeDuration';
const VALID_SNOOZE_DURATIONS: Array<String> = ['0h', '1h', '2h', '3h', '4h', '5h', '6h', '7h', '8h', '9h', '10h', '11h', '12h'];

export interface SnoozeLatestParams {
    buildTimestamp: string;
}

export interface SnoozeLatestResponse {
    status: SnoozeStatus;
    snooze?: SnoozeRequest;
    error?: string;
}

export interface SubmitSnoozeParams {
    buildTimestamp: string;
    snoozeDuration: string;
    snoozeEventTimestamp: string;
}

export interface SubmitSnoozeResponse {
    error?: string;
    code?: number;
    snooze?: SnoozeRequest;
}

export async function getSnoozeStatus(params: SnoozeLatestParams): Promise<SnoozeLatestResponse> {
    // Get the latest snooze request from the database.
    // Check if the snooze request is active, expired, or none.
    // Result: ACTIVE, EXPIRED, NONE
    const buildTimestamp = params.buildTimestamp;

    // Get the current event timestamp from the database.
    let eventsCurrent = null;
    try {
        eventsCurrent = await SensorEventDatabase.get(buildTimestamp);
    } catch (error) {
        console.error(error);
        return <SnoozeLatestResponse> {
            status: SnoozeStatus.NONE,
        };
    }
    if (!eventsCurrent) {
        console.error('No current event');
return <SnoozeLatestResponse> {
            status: SnoozeStatus.NONE,
        };
    }
    if (!eventsCurrent.currentEvent || !eventsCurrent.currentEvent.timestampSeconds) {
        console.error('No current event timestamp');
        return <SnoozeLatestResponse> {
            status: SnoozeStatus.NONE,
        };
    }
    const currentEventTimestampSeconds = parseInt(eventsCurrent.currentEvent.timestampSeconds);
    if (typeof currentEventTimestampSeconds !== 'number') {
        console.error('Invalid current event timestamp');
        return <SnoozeLatestResponse> {
            status: SnoozeStatus.NONE,
        };
    }

    // Compare the latest event with the latest snooze request to make sure they match.
    const nowSeconds = firebase.firestore.Timestamp.now().seconds;
    try {
        const snoozeResult: SnoozeRequest = await SnoozeNotificationsDatabase.get(buildTimestamp);
        if (!snoozeResult || !snoozeResult.snoozeEndTimeSeconds) {
            console.info('No snooze request');
            return <SnoozeLatestResponse> {
                status: SnoozeStatus.NONE,
            };
        }
        console.log(snoozeResult);

        if (snoozeResult.currentEventTimestampSeconds !== currentEventTimestampSeconds) {
            console.info('Snooze request does not match current event');
            return <SnoozeLatestResponse> {
                status: SnoozeStatus.NONE,
            };
        }

        if (nowSeconds > snoozeResult.snoozeEndTimeSeconds) {
            console.info('Snooze request expired');
            return <SnoozeLatestResponse> {
                status: SnoozeStatus.EXPIRED,
                snooze: snoozeResult,
            };
        }
        return <SnoozeLatestResponse> {
            status: SnoozeStatus.ACTIVE,
            snooze: snoozeResult,
        };
    } catch (error) {
        console.error(error)
        return <SnoozeLatestResponse> {
            status: SnoozeStatus.NONE,
            error: error.toString(),
        };
    }
}

export async function submitSnoozeNotificationsRequest(params: SubmitSnoozeParams): Promise<SubmitSnoozeResponse> {
    const buildTimestamp = params.buildTimestamp;
    const snoozeDuration = params.snoozeDuration;
    const snoozeEventTimestamp = params.snoozeEventTimestamp;

    // Get the current event timestamp from the database.
    let eventsCurrent = null;
    try {
        eventsCurrent = await SensorEventDatabase.get(buildTimestamp);
    } catch (error) {
        console.error(error);
        return <SubmitSnoozeResponse>{ error: 'Error getting current event', code: 500 };
    }
    if (!eventsCurrent) {
        console.error('No current event');
        return <SubmitSnoozeResponse>{ error: 'No current event', code: 500 };
    }
    if (!eventsCurrent.currentEvent || !eventsCurrent.currentEvent.timestampSeconds) {
        console.error('No current event timestamp');
        return <SubmitSnoozeResponse>{ error: 'No current event timestamp', code: 500 };
    }
    const currentEventTimestampSeconds = parseInt(eventsCurrent.currentEvent.timestampSeconds);

    // The request is only valid if the snooze event timestamp matches the current event.
    if (currentEventTimestampSeconds !== parseInt(snoozeEventTimestamp)) {
        console.log('currentEventTimestampSeconds:', currentEventTimestampSeconds);
        console.log('snoozeEventTimestamp:', snoozeEventTimestamp);
        console.error('Snooze event timestamp does not match current event timestamp');
        return <SubmitSnoozeResponse>{ error: 'Snooze event timestamp does not match current event timestamp', code: 404 };
    }

    // Get the snooze duration from the request.
    // This is a String from an enumerated list of possoble values.
    if (!VALID_SNOOZE_DURATIONS.includes(snoozeDuration)) {
        console.error('Invalid snooze duration:', snoozeDuration);
        return <SubmitSnoozeResponse>{
            error: 'Invalid snooze duration: ' + snoozeDuration +
                '. Must be one of ' + VALID_SNOOZE_DURATIONS.toString(),
            code: 404,
        };
    }
    // Convert the valid parameter into a number of seconds.
    let durationSeconds: number | null = null;
    if (snoozeDuration && snoozeDuration.endsWith('h')) {
        const durationHours = parseInt(snoozeDuration, 10);
        // Support 0h to 12h snooze durations.
        if (durationHours >= 0 && durationHours <= 12) {
            durationSeconds = durationHours * 60 * 60;
        } else {
            console.error("Invalid snooze duration value:", snoozeDuration);
        }
    } else {
        console.error("Invalid snooze duration format:", snoozeDuration);
    }
    if (durationSeconds === null || typeof durationSeconds !== 'number') {
        console.error('Snooze duration is invalid:', snoozeDuration);
        return <SubmitSnoozeResponse>{
            error: 'Invalid parameter, ' + SNOOZE_DURATION_PARAM_KEY +
                ': ' + snoozeDuration,
            code: 500,
        };
    }

    // Calculate the snooze end time.
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
        return <SubmitSnoozeResponse>{ snooze: snoozeResult };
    } catch (error) {
        console.error(error);
        return <SubmitSnoozeResponse>{ error: 'Error saving snooze request', code: 500 };
    }
}
