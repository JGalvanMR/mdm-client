package com.mdm.client.core

object Constants {

    // ── Canales de notificación ────────────────────────────────────────────────
    const val NOTIF_CHANNEL_SERVICE   = "mdm_service_channel"
    const val NOTIF_CHANNEL_ALERTS    = "mdm_alerts_channel"
    const val NOTIF_ID_SERVICE        = 1001
    const val NOTIF_ID_ALERT          = 1002

    // ── Broadcasts internos ────────────────────────────────────────────────────
    const val ACTION_START_KIOSK      = "com.mdm.client.ACTION_START_KIOSK"
    const val ACTION_STOP_KIOSK       = "com.mdm.client.ACTION_STOP_KIOSK"
    const val ACTION_UPDATE_UI        = "com.mdm.client.ACTION_UPDATE_UI"
    const val ACTION_SERVICE_STATUS   = "com.mdm.client.ACTION_SERVICE_STATUS"

    // ── Extras de broadcast ────────────────────────────────────────────────────
    const val EXTRA_LOG_MESSAGE       = "log_message"
    const val EXTRA_COMMANDS_COUNT    = "commands_count"
    const val EXTRA_LAST_POLL         = "last_poll"

    // ── WorkManager ────────────────────────────────────────────────────────────
    const val WORK_MDM_SYNC           = "mdm_sync_work"
    const val WORK_MDM_PERIODIC       = "mdm_periodic_sync"

    // ── Límites ────────────────────────────────────────────────────────────────
    const val MAX_LOG_LINES           = 100
    const val MAX_PENDING_COMMANDS    = 10
    const val TOKEN_MIN_LENGTH        = 32

    // ── SharedPrefs keys ──────────────────────────────────────────────────────
    const val PREF_FILE               = "mdm_secure_prefs"
    const val KEY_TOKEN               = "device_token"
    const val KEY_DEVICE_ID           = "device_id"
    const val KEY_IS_REGISTERED       = "is_registered"
    const val KEY_KIOSK_ENABLED       = "kiosk_enabled"
    const val KEY_CAMERA_DISABLED     = "camera_disabled"
    const val KEY_COMMANDS_EXECUTED   = "commands_executed"
    const val KEY_LAST_POLL_TS        = "last_poll_timestamp"
    const val KEY_LAST_SYNC_TS        = "last_sync_timestamp"
    const val KEY_REGISTRATION_RETRY  = "registration_retry_count"
	const val GRANT_SCREEN_CAPTURE = "GRANT_SCREEN_CAPTURE"
}