package com.grimoire.android.data

import android.content.Context
import android.util.Log
import com.grimoire.android.native.GrimoireNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Manages the on-device grimoire database.
 *
 * First-launch flow (from the app):
 *   1. Download the gzipped DB from the GitHub release (static link).
 *   2. Verify the .gz against the release's SHA256SUMS manifest.
 *   3. Decompress it to a temp DB.
 *   4. Verify the decompressed DB against the same manifest.
 *   5. Atomically rename it into place and open it read-only.
 */
object GrimoireDatabase {

    const val DB_NAME = "grimoire.db"

    private const val DB_GZ_URL =
        "https://github.com/sagaciouszu/destiny-agent-grimoire/releases/latest/download/grimoire.db.gz"
    private const val SUMS_URL =
        "https://github.com/sagaciouszu/destiny-agent-grimoire/releases/latest/download/SHA256SUMS"

    private const val TAG = "GrimoireDatabase"

    fun dbFile(context: Context): File = File(context.filesDir, DB_NAME)
    private fun gzFile(context: Context): File = File(context.filesDir, "$DB_NAME.gz")
    private fun tmpDbFile(context: Context): File = File(context.filesDir, "$DB_NAME.tmp")

    /** true when a verified DB exists and the native engine can open it. */
    fun isReady(context: Context): Boolean {
        val f = dbFile(context)
        if (!f.exists() || f.length() < 1024 * 1024) return false
        return GrimoireNative.nativeOpenDb(f.absolutePath).isNotEmpty()
    }

    /** Removes the downloaded DB and any partial files. */
    fun delete(context: Context) {
        runCatching { GrimoireNative.nativeClose() }
        listOf(dbFile(context), gzFile(context), tmpDbFile(context)).forEach {
            if (it.exists()) it.delete()
        }
    }

    /** Parse the SHA256SUMS manifest: "<sha256>  <filename>" lines. */
    internal fun parseSums(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val idx = line.indexOfFirst { it.isWhitespace() }
                if (idx <= 0) null
                else line.substring(0, idx).lowercase() to line.substring(idx + 1).trim()
            }
            .toMap()

    sealed interface DlState {
        data object Connecting : DlState
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DlState
        data object VerifyingCompressed : DlState
        data object Decompressing : DlState
        data object VerifyingDecompressed : DlState
        data object Ready : DlState
        data class Failed(val message: String) : DlState
    }

    private fun sha256Hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hashStreaming(input: java.io.InputStream, bufferSize: Int = 1 shl 20): MessageDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { ins ->
            val buf = ByteArray(bufferSize)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest
    }

    private fun httpGet(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "grimoire-android")
        return conn
    }

    /**
     * Downloads, verifies, decompresses, verifies, atomically installs the DB.
     * Calls [onState] with every stage transition. Safe to call from a background thread.
     */
    suspend fun downloadAndVerify(
        context: Context,
        onState: (DlState) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val gz = gzFile(context)
        val tmpDb = tmpDbFile(context)
        try {
            onState(DlState.Connecting)

            // --- 1. Fetch the checksum manifest ---
            val sumsText: String
            val conn = httpGet(SUMS_URL)
            try {
                sumsText = conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val sums = parseSums(sumsText)
            val expectedGz = sums["grimoire.db.gz"]
                ?: throw IllegalStateException("grimoire.db.gz not found in SHA256SUMS")
            val expectedDb = sums[DB_NAME]

            Log.i(TAG, "manifest loaded; expected .gz sha256 = $expectedGz")

            // --- 2. Download the .gz while hashing ---
            val digest = MessageDigest.getInstance("SHA-256")
            val dlConn = httpGet(DB_GZ_URL)
            val totalBytes = dlConn.contentLengthLong
            try {
                dlConn.inputStream.use { input ->
                    FileOutputStream(gz).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            downloaded += n
                            onState(DlState.Downloading(downloaded, totalBytes))
                        }
                    }
                }
            } finally {
                dlConn.disconnect()
            }

            // --- 3. Verify the compressed payload ---
            onState(DlState.VerifyingCompressed)
            val gzHash = sha256Hex(digest.digest())
            if (!gzHash.equals(expectedGz, ignoreCase = true)) {
                throw IllegalStateException("SHA-256 mismatch for .gz: got $gzHash, expected $expectedGz")
            }

            // --- 4. Decompress into a temp DB while hashing ---
            onState(DlState.Decompressing)
            val dbDigest = MessageDigest.getInstance("SHA-256")
            GZIPInputStream(FileInputStream(gz)).use { input ->
                FileOutputStream(tmpDb).use { out ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        dbDigest.update(buf, 0, n)
                    }
                }
            }

            // --- 5. Verify the decompressed DB ---
            onState(DlState.VerifyingDecompressed)
            if (expectedDb != null) {
                val dbHash = sha256Hex(dbDigest.digest())
                if (!dbHash.equals(expectedDb, ignoreCase = true)) {
                    throw IllegalStateException("SHA-256 mismatch for decompressed db: got $dbHash, expected $expectedDb")
                }
            }

            // --- 6. Atomic install ---
            val db = dbFile(context)
            if (db.exists()) db.delete()
            if (!tmpDb.renameTo(db)) {
                tmpDb.copyTo(db, overwrite = true)
                tmpDb.delete()
            }
            gz.delete()

            // --- 7. Open it to be sure it's usable ---
            val opened = GrimoireNative.nativeOpenDb(db.absolutePath)
            if (opened.isEmpty()) {
                throw IllegalStateException("downloaded database failed the native integrity check")
            }
            onState(DlState.Ready)
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            runCatching { gz.delete() }
            runCatching { tmpDb.delete() }
            onState(DlState.Failed(e.message ?: e.javaClass.simpleName))
        }
    }
}
