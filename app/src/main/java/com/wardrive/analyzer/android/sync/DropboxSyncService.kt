package com.wardrive.analyzer.android.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class DropboxSyncService {
    fun syncFromDropbox(token: String, remoteFolder: String, appFilesDir: File): File {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            throw IllegalArgumentException("Dropbox access token is required.")
        }
        val folder = normalizeFolder(remoteFolder)
        val latestPath = "$folder/latest_project.zip"
        val outDir = File(appFilesDir, "dropbox_sync").apply { mkdirs() }
        val zipFile = File(outDir, "latest_project.zip")
        downloadFile(cleanToken, latestPath, zipFile)
        val unzipDir = File(outDir, "latest_project").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        unzip(zipFile, unzipDir)
        return unzipDir
    }

    private fun normalizeFolder(path: String): String {
        var p = path.trim()
        if (p.isEmpty()) p = "/WardriveAnalyzerSync"
        if (!p.startsWith("/")) p = "/$p"
        return p.trimEnd('/')
    }

    private fun downloadFile(token: String, remotePath: String, outFile: File) {
        val conn = (URL("https://content.dropboxapi.com/2/files/download").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Dropbox-API-Arg", JSONObject(mapOf("path" to remotePath)).toString())
            setRequestProperty("Content-Type", "application/octet-stream")
            connectTimeout = 30000
            readTimeout = 120000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw RuntimeException("Dropbox download failed: $err")
        }
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
    }

    private fun unzip(zipFile: File, outputDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

