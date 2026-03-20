package com.mdm.client.data.network

import com.mdm.client.BuildConfig

object ApiEndpoints {
    private val BASE = BuildConfig.SERVER_URL.trimEnd('/')

    // Device (autenticadas con Device-Token)
    val REGISTER        = "$BASE/api/device/register"
    val POLL            = "$BASE/api/device/poll"
    val COMMAND_RESULT  = "$BASE/api/device/command-result"
    val HEARTBEAT       = "$BASE/api/device/heartbeat"
}