package com.mdm.client.commands.handlers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker
import java.net.HttpURLConnection
import java.net.URL

class AppManagementHandler(private val context: Context) {

        private val TAG = "AppManagementHandler"
        private val checker = DeviceOwnerChecker(context)
        private val gson = Gson()

        // ── INSTALL_APP ─────────────────────────────────────────────────────────────
        // Descarga el APK desde una URL y lo instala silenciosamente (Device Owner)
        fun installApp(parametersJson: String?): ExecutionResult {
                if (!checker.isDeviceOwner())
                        return ExecutionResult.failure(
                                "Device Owner requerido para instalación silenciosa."
                        )

                return try {
                        val params = gson.fromJson(parametersJson, Map::class.java)
                        val url =
                                params["url"] as? String
                                        ?: return ExecutionResult.failure("Falta parámetro 'url'.")
                        val pkgName =
                                params["packageName"] as? String
                                        ?: return ExecutionResult.failure("Falta 'packageName'.")

                        MdmLog.i(TAG, "Descargando APK desde $url")

                        // Descargar APK
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = 30_000
                        conn.readTimeout = 120_000
                        conn.connect()

                        if (conn.responseCode != HttpURLConnection.HTTP_OK)
                                return ExecutionResult.failure(
                                        "Error descargando APK: HTTP ${conn.responseCode}"
                                )

                        val apkBytes = conn.inputStream.use { it.readBytes() }
                        conn.disconnect()

                        MdmLog.i(TAG, "APK descargado: ${apkBytes.size / 1024}KB. Instalando...")

                        // Instalar usando PackageInstaller
                        val pi = context.packageManager.packageInstaller
                        val params2 =
                                PackageInstaller.SessionParams(
                                                PackageInstaller.SessionParams.MODE_FULL_INSTALL
                                        )
                                        .apply { setAppPackageName(pkgName) }

                        val sessionId = pi.createSession(params2)
                        pi.openSession(sessionId).use { session ->
                                session.openWrite("package", 0, apkBytes.size.toLong()).use { os ->
                                        os.write(apkBytes)
                                        session.fsync(os)
                                }

                                val intent = Intent("com.mdm.client.INSTALL_COMPLETE")
                                val pendingIntent =
                                        PendingIntent.getBroadcast(
                                                context,
                                                sessionId,
                                                intent,
                                                PendingIntent.FLAG_UPDATE_CURRENT or
                                                        PendingIntent.FLAG_MUTABLE
                                        )
                                session.commit(pendingIntent.intentSender)
                        }

                        MdmLog.i(TAG, "Instalación de $pkgName iniciada.")
                        ExecutionResult.success(
                                """{"packageName":"$pkgName","status":"installing"}"""
                        )
                } catch (e: Exception) {
                        MdmLog.e(TAG, "Error instalando app: ${e.message}", e)
                        ExecutionResult.failure("Error en instalación: ${e.message}")
                }
        }

        // ── UNINSTALL_APP ─────────────────────────────────────────────────────────
        fun uninstallApp(parametersJson: String?): ExecutionResult {
                if (!checker.isDeviceOwner())
                        return ExecutionResult.failure(
                                "Device Owner requerido para desinstalar silenciosamente."
                        )

                return try {
                        val params = gson.fromJson(parametersJson, Map::class.java)
                        val pkgName =
                                params["packageName"] as? String
                                        ?: return ExecutionResult.failure(
                                                "Falta parámetro 'packageName'."
                                        )

                        // Verificar que no sea una app del sistema crítica
                        val protectedPackages =
                                setOf("android", "com.android.systemui", context.packageName)
                        if (pkgName in protectedPackages)
                                return ExecutionResult.failure(
                                        "No se puede desinstalar el paquete protegido: $pkgName"
                                )

                        val pi = context.packageManager.packageInstaller
                        val intent = Intent("com.mdm.client.UNINSTALL_COMPLETE")
                        val pending =
                                PendingIntent.getBroadcast(
                                        context,
                                        pkgName.hashCode(),
                                        intent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                                PendingIntent.FLAG_MUTABLE
                                )
                        pi.uninstall(pkgName, pending.intentSender)

                        MdmLog.i(TAG, "Desinstalación de $pkgName iniciada.")
                        ExecutionResult.success(
                                """{"packageName":"$pkgName","status":"uninstalling"}"""
                        )
                } catch (e: Exception) {
                        MdmLog.e(TAG, "Error desinstalando: ${e.message}", e)
                        ExecutionResult.failure("Error desinstalando: ${e.message}")
                }
        }

        // ── LIST_APPS ─────────────────────────────────────────────────────────────
        fun listApps(): ExecutionResult {
                return try {
                        val pm = context.packageManager
                        val flags = PackageManager.GET_META_DATA

                        @Suppress("DEPRECATION")
                        val packages: List<PackageInfo> = pm.getInstalledPackages(flags)

                        val apps =
                                packages
                                        .filter { it.applicationInfo != null }
                                        .filter {
                                                // Solo apps del usuario (no del sistema)
                                                (it.applicationInfo!!.flags and
                                                        android.content.pm.ApplicationInfo
                                                                .FLAG_SYSTEM) == 0
                                        }
                                        .map { pkg ->
                                                mapOf(
                                                        "packageName" to pkg.packageName,
                                                        "appName" to
                                                                pkg.applicationInfo!!
                                                                        .loadLabel(pm)
                                                                        .toString(),
                                                        "versionName" to (pkg.versionName ?: ""),
                                                        "versionCode" to pkg.longVersionCode
                                                )
                                        }
                                        .sortedBy { it["appName"] as String }

                        val json = gson.toJson(mapOf("count" to apps.size, "apps" to apps))
                        MdmLog.i(TAG, "Lista de ${apps.size} apps generada.")
                        ExecutionResult.success(json)
                } catch (e: Exception) {
                        MdmLog.e(TAG, "Error listando apps: ${e.message}", e)
                        ExecutionResult.failure("Error: ${e.message}")
                }
        }

        // ── CLEAR_APP_DATA ────────────────────────────────────────────────────────
        fun clearAppData(parametersJson: String?): ExecutionResult {
                if (!checker.isDeviceOwner())
                        return ExecutionResult.failure(
                                "Device Owner requerido para limpiar datos de apps."
                        )

                return try {
                        val params = gson.fromJson(parametersJson, Map::class.java)
                        val pkgName =
                                params["packageName"] as? String
                                        ?: return ExecutionResult.failure(
                                                "Falta parámetro 'packageName'."
                                        )

                        if (pkgName == context.packageName)
                                return ExecutionResult.failure(
                                        "No se pueden limpiar los datos de la propia app MDM."
                                )

                        // API 28+ — DevicePolicyManager.clearApplicationUserData()
                        // El callback recibe (packageName: String, succeeded: Boolean)
                        checker.getDpm().clearApplicationUserData(
                                        checker.adminComponent,
                                        pkgName,
                                        context.mainExecutor
                                ) { pkg, succeeded ->
                                MdmLog.i(TAG, "Datos limpiados [$pkg]: $succeeded")
                        }

                        MdmLog.i(TAG, "Limpieza de datos iniciada para: $pkgName")
                        ExecutionResult.success("""{"packageName":"$pkgName","initiated":true}""")
                } catch (e: SecurityException) {
                        MdmLog.e(TAG, "SecurityException limpiando datos: ${e.message}")
                        ExecutionResult.failure("Permiso denegado: ${e.message}")
                } catch (e: Exception) {
                        MdmLog.e(TAG, "Error limpiando datos: ${e.message}", e)
                        ExecutionResult.failure("Error: ${e.message}")
                }
        }
}
