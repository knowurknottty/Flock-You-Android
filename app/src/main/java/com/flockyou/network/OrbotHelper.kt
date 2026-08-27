package com.flockyou.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrbotHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OrbotHelper"
        const val ORBOT_PACKAGE_NAME = "org.torproject.android"
        const val ORBOT_GITHUB_UNIVERSAL_URL = "https://github.com/guardianproject/orbot-android/releases/download/17.9.5-RC-4-tor-0.4.9.11/Orbot-17.9.5-RC-4-tor-0.4.9.11-fullperm-universal-release.apk"
        const val ORBOT_GITHUB_ARM64_URL = "https://github.com/guardianproject/orbot-android/releases/download/17.9.5-RC-4-tor-0.4.9.11/Orbot-17.9.5-RC-4-tor-0.4.9.11-fullperm-arm64-v8a-release.apk"
        const val ORBOT_MEGA_ARM64_URL = "https://mega.nz/file/bjogRAbY#8bItqL2nKSL2JEnB39d2VbUe4KgG8GtzagFmcHtPIV0"
        const val ORBOT_MEGA_UNIVERSAL_URL = "https://mega.nz/file/amBj0KLa#ldnGCu6RMJJ0oZTf7hKDpzFzfgRDZQRiJmOZnMCWpNk"
        const val DEFAULT_SOCKS_PORT = 9050
        const val DEFAULT_HTTP_PORT = 8118
        private const val CONNECTION_TIMEOUT_MS = 3000

        fun preferredInstallUrl(supportedAbis: Array<String> = Build.SUPPORTED_ABIS): String =
            if (supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }) {
                ORBOT_MEGA_ARM64_URL
            } else {
                ORBOT_MEGA_UNIVERSAL_URL
            }
    }

    fun isOrbotInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    ORBOT_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(ORBOT_PACKAGE_NAME, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun isOrbotRunning(
        host: String = "127.0.0.1",
        port: Int = DEFAULT_SOCKS_PORT
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Orbot not running on $host:$port - ${e.message}")
            false
        }
    }

    fun launchOrbot() {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Log.w(TAG, "Could not get launch intent for Orbot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Orbot", e)
        }
    }

    fun openOrbotInstallPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preferredInstallUrl())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Orbot install page", e)
        }
    }

    fun requestOrbotStart() {
        try {
            // Send broadcast to request Orbot to start
            val intent = Intent("org.torproject.android.intent.action.START").apply {
                setPackage(ORBOT_PACKAGE_NAME)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Orbot start", e)
        }
    }
}
