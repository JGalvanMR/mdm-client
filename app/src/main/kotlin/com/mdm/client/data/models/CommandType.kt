package com.mdm.client.data.models

object CommandType {
    // ── Existentes ─────────────────────────────────────────────────────────────
    const val LOCK_DEVICE = "LOCK_DEVICE"
    const val WAKE_SCREEN = "WAKE_SCREEN"
    const val DISABLE_CAMERA = "DISABLE_CAMERA"
    const val ENABLE_CAMERA = "ENABLE_CAMERA"
    const val ENABLE_KIOSK_MODE = "ENABLE_KIOSK_MODE"
    const val DISABLE_KIOSK_MODE = "DISABLE_KIOSK_MODE"
    const val GET_DEVICE_INFO = "GET_DEVICE_INFO"
    const val REBOOT_DEVICE = "REBOOT_DEVICE"
    const val WIPE_DATA = "WIPE_DATA"
    const val SET_SCREEN_TIMEOUT = "SET_SCREEN_TIMEOUT"

    // ── Fase C: Nuevos ────────────────────────────────────────────────────────
    const val INSTALL_APP = "INSTALL_APP" // params: {url, packageName}
    const val UNINSTALL_APP = "UNINSTALL_APP" // params: {packageName}
    const val LIST_APPS = "LIST_APPS" // sin params
    const val ENABLE_WIFI = "ENABLE_WIFI"
    const val DISABLE_WIFI = "DISABLE_WIFI"
    const val SET_WIFI_CONFIG = "SET_WIFI_CONFIG" // params: {ssid, password, security}
    const val CLEAR_APP_DATA = "CLEAR_APP_DATA" // params: {packageName}
    const val GET_LOCATION = "GET_LOCATION"
    const val SET_VOLUME = "SET_VOLUME" // params: {level: 0-100}
    const val ENABLE_BLUETOOTH = "ENABLE_BLUETOOTH"
    const val DISABLE_BLUETOOTH = "DISABLE_BLUETOOTH"
    const val SET_BRIGHTNESS = "SET_BRIGHTNESS" // params: {level: 0-255}
    const val SEND_MESSAGE = "SEND_MESSAGE" // params: {title, body} — notificación local

    val ALL =
            setOf(
                    LOCK_DEVICE,
                    WAKE_SCREEN,
                    DISABLE_CAMERA,
                    ENABLE_CAMERA,
                    ENABLE_KIOSK_MODE,
                    DISABLE_KIOSK_MODE,
                    GET_DEVICE_INFO,
                    REBOOT_DEVICE,
                    WIPE_DATA,
                    SET_SCREEN_TIMEOUT,
                    INSTALL_APP,
                    UNINSTALL_APP,
                    LIST_APPS,
                    ENABLE_WIFI,
                    DISABLE_WIFI,
                    SET_WIFI_CONFIG,
                    CLEAR_APP_DATA,
                    GET_LOCATION,
                    SET_VOLUME,
                    ENABLE_BLUETOOTH,
                    DISABLE_BLUETOOTH,
                    SET_BRIGHTNESS,
                    SEND_MESSAGE
            )

    val REQUIRES_MAIN_THREAD = setOf(ENABLE_KIOSK_MODE, DISABLE_KIOSK_MODE)
    val DESTRUCTIVE = setOf(REBOOT_DEVICE, WIPE_DATA, CLEAR_APP_DATA, UNINSTALL_APP)
}
