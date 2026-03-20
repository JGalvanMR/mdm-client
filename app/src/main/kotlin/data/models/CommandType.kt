package com.mdm.client.data.models

object CommandType {
    const val LOCK_DEVICE        = "LOCK_DEVICE"
    const val DISABLE_CAMERA     = "DISABLE_CAMERA"
    const val ENABLE_CAMERA      = "ENABLE_CAMERA"
    const val ENABLE_KIOSK_MODE  = "ENABLE_KIOSK_MODE"
    const val DISABLE_KIOSK_MODE = "DISABLE_KIOSK_MODE"
    const val GET_DEVICE_INFO    = "GET_DEVICE_INFO"
    const val REBOOT_DEVICE      = "REBOOT_DEVICE"       // requiere Device Owner
    const val WIPE_DATA          = "WIPE_DATA"           // factory reset, cuidado
    const val SET_SCREEN_TIMEOUT = "SET_SCREEN_TIMEOUT"  // parámetro: seconds

    val ALL = setOf(
        LOCK_DEVICE, DISABLE_CAMERA, ENABLE_CAMERA,
        ENABLE_KIOSK_MODE, DISABLE_KIOSK_MODE, GET_DEVICE_INFO,
        REBOOT_DEVICE, WIPE_DATA, SET_SCREEN_TIMEOUT
    )

    // Comandos que requieren estar en el Main thread (interacción con UI/Activity)
    val REQUIRES_MAIN_THREAD = setOf(ENABLE_KIOSK_MODE, DISABLE_KIOSK_MODE)

    // Comandos destructivos que requieren confirmación doble (parámetro "confirm": true)
    val DESTRUCTIVE = setOf(REBOOT_DEVICE, WIPE_DATA)
}