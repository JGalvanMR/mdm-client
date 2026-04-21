package com.mdm.client.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mdm.client.BuildConfig
import com.mdm.client.core.MdmResult
import com.mdm.client.data.models.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

class ApiClient {

        private val TAG = "ApiClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val gson: Gson = GsonBuilder().setLenient().create()

        private val client: OkHttpClient by lazy {
                val logging =
                        HttpLoggingInterceptor { Log.d("MDM_HTTP", it) }.apply {
                                level =
                                        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                                        else HttpLoggingInterceptor.Level.NONE
                        }
                OkHttpClient.Builder()
                        .connectTimeout(
                                BuildConfig.CONNECT_TIMEOUT_SECONDS.toLong(),
                                TimeUnit.SECONDS
                        )
                        .readTimeout(BuildConfig.READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .callTimeout(30, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .addInterceptor(logging)
                        .addInterceptor { chain ->
                                val req = chain.request()
                                val resp = chain.proceed(req)
                                if (!resp.isSuccessful)
                                        Log.w(TAG, "HTTP ${resp.code} en ${req.url.encodedPath}")
                                resp
                        }
                        .build()
        }

        // ── API pública ───────────────────────────────────────────────────────────

        fun registerDevice(req: RegisterDeviceRequest): MdmResult<RegisterDeviceResponse> =
                postWrapped(ApiEndpoints.REGISTER, req)

        fun poll(token: String, req: PollRequest): MdmResult<PollResponse> =
                postWrappedAuth(ApiEndpoints.POLL, token, req)

        fun reportCommandResult(token: String, req: CommandResultRequest): MdmResult<Unit> =
                postWrappedAuth(ApiEndpoints.COMMAND_RESULT, token, req)

        fun heartbeat(token: String, req: HeartbeatRequest): MdmResult<Unit> =
                postWrappedAuth(ApiEndpoints.HEARTBEAT, token, req)

        // ApiClient.kt — agregar este método sin tocar los existentes
        fun reportTelemetry(token: String, report: TelemetryReport): MdmResult<Unit> =
                postWrappedAuth(ApiEndpoints.TELEMETRY, token, report)

        // ── Helpers: POST con y sin auth, con unwrap automático ───────────────────

        private inline fun <reified T> postWrapped(url: String, body: Any): MdmResult<T> {
                val request =
                        Request.Builder()
                                .url(url)
                                .post(gson.toJson(body).toRequestBody(JSON))
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .build()
                return executeAndUnwrap(request)
        }

        private inline fun <reified T> postWrappedAuth(
                url: String,
                token: String,
                body: Any
        ): MdmResult<T> {
                val request =
                        Request.Builder()
                                .url(url)
                                .post(gson.toJson(body).toRequestBody(JSON))
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .header("Device-Token", token)
                                .build()
                return executeAndUnwrap(request)
        }

        /**
         * Ejecuta la petición HTTP y desenvuelve el ApiResponse<T> del servidor. Si success=false
         * retorna Failure con el mensaje de error del servidor. Si T es Unit, solo verifica success
         * sin intentar parsear data.
         */
        private inline fun <reified T> executeAndUnwrap(request: Request): MdmResult<T> {
                return try {
                        client.newCall(request).execute().use { response ->
                                val bodyStr = response.body?.string()

                                // Errores HTTP antes de intentar parsear
                                when (response.code) {
                                        401 ->
                                                return MdmResult.Failure(
                                                        "Token inválido (401).",
                                                        errorCode = 401
                                                )
                                        403 ->
                                                return MdmResult.Failure(
                                                        "Acceso denegado (403).",
                                                        errorCode = 403
                                                )
                                        404 ->
                                                return MdmResult.Failure(
                                                        "Endpoint no encontrado (404).",
                                                        errorCode = 404
                                                )
                                        429 ->
                                                return MdmResult.Failure(
                                                        "Rate limit excedido (429).",
                                                        errorCode = 429
                                                )
                                }

                                if (bodyStr.isNullOrBlank()) {
                                        return MdmResult.Failure("Respuesta vacía del servidor.")
                                }

                                // Para endpoints que retornan solo success/message (Unit)
                                if (T::class == Unit::class) {
                                        val wrapper = gson.fromJson(bodyStr, ApiWrapper::class.java)
                                        return if (wrapper?.success == true)
                                                @Suppress("UNCHECKED_CAST")
                                                MdmResult.Success(Unit as T)
                                        else
                                                MdmResult.Failure(
                                                        wrapper?.error
                                                                ?: wrapper?.message
                                                                        ?: "Error del servidor."
                                                )
                                }

                                // Para endpoints con data tipada
                                val type =
                                        TypeToken.getParameterized(
                                                        ApiWrapper::class.java,
                                                        T::class.java
                                                )
                                                .type
                                val wrapper = gson.fromJson<ApiWrapper<T>>(bodyStr, type)

                                when {
                                        wrapper == null ->
                                                MdmResult.Failure(
                                                        "No se pudo parsear la respuesta."
                                                )
                                        !wrapper.success ->
                                                MdmResult.Failure(
                                                        wrapper.error
                                                                ?: wrapper.message
                                                                        ?: "Error del servidor."
                                                )
                                        wrapper.data == null ->
                                                MdmResult.Failure(
                                                        "El servidor retornó success=true pero sin datos."
                                                )
                                        else -> MdmResult.Success(wrapper.data)
                                }
                        }
                } catch (e: UnknownHostException) {
                        MdmResult.Failure("No se puede resolver el host. Verifica SERVER_URL.", e)
                } catch (e: SocketTimeoutException) {
                        MdmResult.Failure("Timeout. El servidor no respondió a tiempo.", e)
                } catch (e: SSLException) {
                        MdmResult.Failure("Error SSL/TLS: ${e.message}", e)
                } catch (e: Exception) {
                        MdmResult.Failure("Error de red: ${e.message}", e)
                }
        }
}
