package com.grimoire.android.native

/**
 * JNI bridge to the native search engine (SQLite + FTS5).
 * The library is loaded once at class init.
 *
 * All functions are safe to call from any thread; native code serializes access.
 */
object GrimoireNative {

    init {
        System.loadLibrary("grimoire_native")
    }

    /** Opens (read-only) and integrity-checks the DB at [path]. Empty string = failure. */
    external fun nativeOpenDb(path: String): String

    /** true when a DB handle is currently open. */
    external fun nativeIsDbOpen(): Boolean

    /** JSON: {"ready":true,"path":"...","rows":120689} */
    external fun nativeDbInfo(): String

    /**
     * JSON: {"results":[{"title","speaker","source","source_slug","snippet"}],
     *        "total":N,"elapsedMs":N.N,"query":"..."}
     * [source] is an FTS source filter (e.g. "transcript_line") or "" for all.
     */
    external fun nativeSearch(query: String, source: String, limit: Int, offset: Int): String

    /** Closes the handle; subsequent searches will fail until nativeOpenDb is called again. */
    external fun nativeClose()
}
