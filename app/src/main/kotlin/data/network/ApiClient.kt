package com.mdm.client.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mdm.client.BuildConfig
import com.mdm.client.core.MdmResult
import com.mdm.client.data.models.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class ApiClient {

    private val TAG = "ApiClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val gson: Gson = GsonBuilder().setLenient().create()

    // ── OkHttpClient singleton ────────────────────────────────────────────────
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { Log.d("MDM_HTTP", it) }.apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .connectTimeout(BuildConfig.CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(BuildConfig.READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            // Interceptor para loguear errores HTTP de forma limpia
            .addInterceptor { chain ->
                val request  = chain.request()
                val response = chain.proceed(request)
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} en ${request.url.encodedPath}")
                }
                response
            }
            .build()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ════════════════════════════════════════════════════════════════════════════

    fun registerDevice(req: RegisterDeviceRequest): MdmResult<RegisterDeviceResponse> =
        post(ApiEndpoints.REGISTER, req, RegisterDeviceResponse::class.java)

    fun poll(token: String, req: PollRequest): MdmResult<PollResponse> =
        postAuth(ApiEndpoints.POLL, token, req, PollResponse::class.java)

    fun reportCommandResult(token: String, req: CommandResultRequest): MdmResult<CommandResultResponse> =
        postAuth(ApiEndpoints.COMMAND_RESULT, token, req, CommandResultResponse::class.java)

    fun heartbeat(token: String, req: HeartbeatRequest): MdmResult<HeartbeatResponse> =
        postAuth(ApiEndpoints.HEARTBEAT, token, req, HeartbeatResponse::class.java)

    // ════════════════════════════════════════════════════════════════════════════
    // HELPERS PRIVADOS
    // ════════════════════════════════════════════════════════════════════════════

    private fun <T> post(url: String, body: Any, cls: Class<T>): MdmResult<T> {
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        return execute(request, cls)
    }

    private fun <T> postAuth(url: String, token: String, body: Any, cls: Class<T>): MdmResult<T> {
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Device-Token", token)
            .build()
        return execute(request, cls)
    }

    private fun <T> execute(request: Request, cls: Class<T>): MdmResult<T> {
        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()

                when {
                    response.code == 401 ->
                        MdmResult.Failure("Token inválido o expirado (401)", errorCode = 401)

                    response.code == 403 ->
                        MdmResult.Failure("Acceso denegado (403)", errorCode = 403)

                    response.code == 404 ->
                        MdmResult.Failure("Endpoint no encontrado (404)", errorCode = 404)

                    response.code == 429 ->
                        MdmResult.Failure("Rate limit excedido (429). Reduce el intervalo de polling.", errorCode = 429)

                    response.code >= 500 ->
                        MdmResult.Failure("Error del servidor ${response.code}: ${bodyStr?.truncateBody()}", errorCode = response.code)

                    !response.isSuccessful ->
                        MdmResult.Failure("HTTP ${response.code}: ${bodyStr?.truncateBody()}", errorCode = response.code)

                    bodyStr.isNullOrBlank() ->
                        MdmResult.Failure("Respuesta vacía del servidor")

                    else -> {
                        val parsed = gson.fromJson(bodyStr, cls)
                        if (parsed != null) MdmResult.Success(parsed)
                        else MdmResult.Failure("Error parseando respuesta JSON")
                    }
                }
            }
        } catch (e: UnknownHostException) {
            MdmResult.Failure("No se puede resolver el host. Verifica SERVER_URL y la red.", e)
        } catch (e: SocketTimeoutException) {
            MdmResult.Failure("Timeout de conexión. El servidor no respondió a tiempo.", e)
        } catch (e: SSLException) {
            MdmResult.Failure("Error SSL/TLS: ${e.message}. Verifica el certificado del servidor.", e)
        } catch (e: Exception) {
            MdmResult.Failure("Error de red: ${e.message}", e)
        }
    }

    private fun String.truncateBody(max: Int = 200) =
        if (length > max) take(max) + "…" else this
}