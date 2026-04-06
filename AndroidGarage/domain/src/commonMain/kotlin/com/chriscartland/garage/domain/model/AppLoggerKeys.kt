package com.chriscartland.garage.domain.model

object AppLoggerKeys {
    // Door updates
    const val INIT_CURRENT_DOOR = "init_current_door"
    const val INIT_RECENT_DOOR = "init_recent_door"
    const val USER_FETCH_CURRENT_DOOR = "user_fetch_current_door"
    const val USER_FETCH_RECENT_DOOR = "user_fetch_recent_door"
    const val FCM_DOOR_RECEIVED = "fcm_door_received"
    const val FCM_SUBSCRIBE_TOPIC = "fcm_subscribe_topic"
    const val ON_CREATE_FCM_SUBSCRIBE_TOPIC = "on_create_fcm_subscribe_topic"

    // Stale data
    const val EXCEEDED_EXPECTED_TIME_WITHOUT_FCM = "exceeded_expected_time_without_fcm"
    const val TIME_WITHOUT_FCM_IN_EXPECTED_RANGE = "time_without_fcm_in_expected_range"

    // Permission
    const val USER_REQUESTED_NOTIFICATION_PERMISSION = "user_requested_notification_permission"

    // Auth
    const val BEGIN_GOOGLE_SIGN_IN = "begin_google_sign_in"
    const val USER_AUTHENTICATED = "user_authenticated"
    const val USER_UNAUTHENTICATED = "user_unauthenticated"
    const val USER_AUTH_UNKNOWN = "user_auth_unknown"
}
