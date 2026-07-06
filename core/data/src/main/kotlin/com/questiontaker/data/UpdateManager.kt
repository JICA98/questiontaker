package com.questiontaker.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val PREFS_NAME = "update_manager_prefs"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/JICA98/questiontaker/releases/latest"
    private const val GITHUB_REPO_URL = "https://github.com/JICA98/questiontaker"

    fun openGitHub(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkForUpdate(
        context: Context,
        forceCheck: Boolean,
        onUpdateAvailable: (tagName: String, downloadUrl: String, releaseNotes: String) -> Unit,
        onNoUpdate: () -> Unit = {}
    ) {
        Thread {
            try {
                val url = URL(LATEST_RELEASE_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "QuestionTaker-App")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val htmlUrl = json.getString("html_url")
                    val releaseNotes = json.optString("body", "No release notes provided.")
                    
                    var downloadUrl = htmlUrl // fallback to releases page
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }

                    val currentVersion = getCurrentVersion(context)
                    if (isNewerVersion(currentVersion, tagName)) {
                        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val skippedVersion = sharedPrefs.getString(KEY_SKIPPED_VERSION, "")
                        
                        if (forceCheck || skippedVersion != tagName) {
                            onUpdateAvailable(tagName, downloadUrl, releaseNotes)
                        } else {
                            onNoUpdate()
                        }
                    } else {
                        onNoUpdate()
                    }
                } else {
                    onNoUpdate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onNoUpdate()
            }
        }.start()
    }

    fun skipVersion(context: Context, tagName: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(KEY_SKIPPED_VERSION, tagName).apply()
    }

    fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.replace("(?i)^v".toRegex(), "").split("-")[0].split(".")
        val cleanLatest = latest.replace("(?i)^v".toRegex(), "").split("-")[0].split(".")
        val length = maxOf(cleanCurrent.size, cleanLatest.size)
        for (i in 0 until length) {
            val currentPart = cleanCurrent.getOrNull(i)?.toIntOrNull() ?: 0
            val latestPart = cleanLatest.getOrNull(i)?.toIntOrNull() ?: 0
            if (latestPart > currentPart) return true
            if (currentPart > latestPart) return false
        }
        return false
    }

    fun startDownload(context: Context, downloadUrl: String, tagName: String) {
        try {
            // Check if it's a fallback web URL rather than a direct APK link
            if (!downloadUrl.endsWith(".apk")) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                return
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("QuestionTaker Update $tagName")
                setDescription("Downloading latest version from GitHub")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "QuestionTaker-$tagName.apk")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(context, "Update download started...", Toast.LENGTH_SHORT).show()

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        try {
                            c.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Suppress
                        }
                        val uri = downloadManager.getUriForDownloadedFile(downloadId)
                        if (uri != null) {
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                c.startActivity(installIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(c, "Download complete. Open downloads to install.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Redirecting to GitHub releases...", Toast.LENGTH_LONG).show()
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }
}
