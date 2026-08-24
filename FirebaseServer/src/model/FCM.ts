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

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#resource:-message
export interface TopicMessage {
  name: string,
  data: { [key: string]: string },
  notification: Notification,
  android: AndroidConfig,
  // webpush: WebpushConfig,
  apns: ApnsConfig,
  // fcm_options: FcmOptions,
  // Union field target can be only one of the following:
  // token: string,
  topic: string,
  // condition: string
  // End of list of possible types for union field target.
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#resource:-message
export interface TokenMessage {
  name: string,
  data: { [key: string]: string },
  notification: Notification,
  android: AndroidConfig,
  // webpush: WebpushConfig,
  // apns: ApnsConfig,
  fcm_options: FcmOptions,
  // Union field target can be only one of the following:
  token: string,
  // topic: string,
  // condition: string
  // End of list of possible types for union field target.
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#resource:-message
export interface ConditionMessage {
  name: string,
  data: { [key: string]: string },
  notification: Notification,
  android: AndroidConfig,
  // webpush: WebpushConfig,
  // apns: ApnsConfig,
  fcm_options: FcmOptions,
  // Union field target can be only one of the following:
  // token: string,
  // topic: string,
  condition: string
  // End of list of possible types for union field target.
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#notification
export interface Notification {
  body: string,
  title: string,
  image: string,
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#androidconfig
export interface AndroidConfig {
  collapse_key: string,
  priority: AndroidMessagePriority,
  ttl: number, // Documentation says this is a string, but the SDK requires a number.
  restricted_package_name: string,
  data: { [key: string]: string },
  notification: AndroidNotification,
  fcm_options: AndroidFcmOptions,
  direct_boot_ok: boolean
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#androidmessagepriority
export enum AndroidMessagePriority {
  NORMAL = 'normal',
  HIGH = 'high',
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#androidnotification
export interface AndroidNotification {
  title: string,
  body: string,
  icon: string,
  color: string,
  sound: string,
  tag: string,
  click_action: string,
  body_loc_key: string,
  body_loc_args: [
    string
  ],
  title_loc_key: string,
  title_loc_args: [
    string
  ],
  channel_id: string,
  ticker: string,
  sticky: boolean,
  event_time: string,
  local_only: boolean,
  notification_priority: NotificationPriority,
  default_sound: boolean,
  default_vibrate_timings: boolean,
  default_light_settings: boolean,
  vibrate_timings: [
    string
  ],
  visibility: Visibility,
  notification_count: number,
  light_settings: LightSettings,
  image: string,
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#notificationpriority
export enum NotificationPriority {
  PRIORITY_UNSPECIFIED = 'PRIORITY_UNSPECIFIED',
  PRIORITY_MIN = 'PRIORITY_MIN',
  PRIORITY_LOW = 'PRIORITY_LOW',
  PRIORITY_DEFAULT = 'PRIORITY_DEFAULT',
  PRIORITY_HIGH = 'PRIORITY_HIGH',
  PRIORITY_MAX = 'PRIORITY_MAX',
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#visibility
export enum Visibility {
  VISIBILITY_UNSPECIFIED = 'private',
  PRIVATE = 'private',
  PUBLIC = 'public',
  SECRET = 'secret',
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#lightsettings
export interface LightSettings {
  // color: Color,
  light_on_duration: string,
  light_off_duration: string,
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#fcmoptions
export interface FcmOptions {
  analytics_label: string,
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#androidfcmoptions
export interface AndroidFcmOptions {
  analytics_label: string,
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#apnsconfig
//
// `headers` are literal HTTP/2 header names APNs itself defines
// (`apns-push-type`, `apns-priority`) — Apple's naming, not Firebase's, so
// they stay hyphenated. `payload.aps` is likewise forwarded to APNs
// byte-for-byte: `content-available` is Apple's own key, hyphenated in the
// wire payload. See docs/DOOR_UPDATE_STRATEGY.md § Phase 2 for why this
// exists — without it, a data-only FCM message never reaches an Apple
// device at all.
export interface ApnsConfig {
  headers: ApnsHeaders,
  payload: ApnsPayload,
  // fcm_options: ApnsFcmOptions,
}

// https://firebase.google.com/docs/reference/apns/apns-request#headers
//
// The index signature matches firebase-admin's own `ApnsConfig.headers`
// type (a plain string map — APNs headers are HTTP/2 headers, so the SDK
// makes no attempt to type them individually). The two named keys are
// this file's own promise about what THIS server sends; TypeScript
// structurally allows both to coexist as long as every named value is
// itself a string.
export interface ApnsHeaders {
  'apns-push-type': ApnsPushType,
  // A background push MUST be priority 5 — APNs rejects priority 10
  // (immediate) for a payload with no visible alert/sound/badge.
  'apns-priority': '5',
  [key: string]: string,
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#apnspushtype
export enum ApnsPushType {
  BACKGROUND = 'background',
}

// https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#apnspayload
export interface ApnsPayload {
  aps: Aps,
}

// https://developer.apple.com/documentation/usernotifications/generating-a-remote-notification
export interface Aps {
  // Wakes the app in the background with no visible UI. Value must be the
  // literal integer 1 (not `true`) — that is Apple's wire format, and
  // firebase-admin's own validation converts a boolean to it, so the
  // literal keeps this file honest about what actually goes over the wire.
  'content-available': 1,
}
