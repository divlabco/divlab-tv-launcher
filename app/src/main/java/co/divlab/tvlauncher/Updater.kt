package co.divlab.tvlauncher

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** เช็คและติดตั้งเวอร์ชันใหม่จาก GitHub Releases */
object Updater {
    private const val API = "https://api.github.com/repos/divlabco/divlab-tv-launcher/releases/latest"
    private const val MIME = "application/vnd.android.package-archive"

    data class Release(val tag: String, val apkUrl: String)

    /** เรียกนอก main thread เท่านั้น */
    fun fetchLatest(): Release? = runCatching {
        val c = URL(API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 8000; c.readTimeout = 8000
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val assets = j.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        Release(j.getString("tag_name"), apk.getString("browser_download_url"))
    }.getOrNull()

    fun isNewer(tag: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
        val a = parts(tag); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun currentVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    fun download(ctx: Context, r: Release): Long {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(r.apkUrl))
            .setTitle("divlab Launcher ${r.tag}")
            .setMimeType(MIME)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "divlab-launcher-${r.tag}.apk")
        return dm.enqueue(req)
    }

    /** คืนค่า (สถานะ, เปอร์เซ็นต์) */
    fun progress(ctx: Context, id: Long): Pair<Int, Int> {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return DownloadManager.STATUS_FAILED to 0
            val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return st to if (total > 0) (done * 100 / total).toInt() else 0
        }
    }

    fun install(ctx: Context, id: Long): Boolean {
        val uri = ctx.getSystemService(DownloadManager::class.java).getUriForDownloadedFile(id) ?: return false
        return runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
