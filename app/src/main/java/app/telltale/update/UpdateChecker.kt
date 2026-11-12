package app.telltale.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val tag: String, val name: String, val notes: String, val apkUrl: String?, val pageUrl: String)

object Versions {
    /** Compares "1.2.3" style versions (a leading "v" and "-suffix" are ignored). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun parseRelease(json: String): ReleaseInfo {
        val o: JsonObject = Json.parseToJsonElement(json).jsonObject
        val apk = o["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        val tag = o["tag_name"]?.jsonPrimitive?.content ?: error("No tag in release")
        return ReleaseInfo(
            tag = tag,
            name = o["name"]?.jsonPrimitive?.content ?: tag,
            notes = o["body"]?.jsonPrimitive?.content ?: "",
            apkUrl = apk,
            pageUrl = o["html_url"]?.jsonPrimitive?.content ?: "",
        )
    }
}

/** Checks the public GitHub "latest release" API (no token) and installs the APK via the system installer. */
class UpdateChecker(private val context: Context, private val repo: String) {

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Telltale-Android")
        c.connectTimeout = 15000; c.readTimeout = 15000
        try {
            if (c.responseCode == 404) error("No releases published yet")
            if (c.responseCode !in 200..299) error("GitHub returned HTTP ${c.responseCode}")
            Versions.parseRelease(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    suspend fun download(url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "telltale-update.apk")
        var u = URL(url)
        var c: HttpURLConnection
        var hops = 0
        while (true) { // follow GitHub's redirect to its CDN
            c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Telltale-Android")
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { u = URL(c.getHeaderField("Location")); c.disconnect(); continue }
            if (code !in 200..299) error("Download failed: HTTP $code")
            break
        }
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        c.disconnect()
        out
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
