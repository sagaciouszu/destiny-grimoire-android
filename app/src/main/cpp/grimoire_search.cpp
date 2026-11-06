// Native search engine for the Destiny Grimoire.
//
// Mirrors the semantics of db/api.py (search, search_total):
//   - query treated as FTS5 syntax when valid, otherwise each whitespace-
//     separated word is quoted and ANDed ("literal" fallback)
//   - ranking: bm25(title, text, speaker, body, source) with item demoted
//     and Narration/Cutscene demoted
//   - snippet() from FTS5 for context with '['/']' match markers
//
// One persistent read-only connection, guarded by a mutex. JSON is the
// interchange format with the Kotlin layer.

#include <android/log.h>
#include <jni.h>
#include <string>
#include <sstream>
#include <chrono>
#include <mutex>
#include <memory>
#include <vector>
#include <cctype>
#include <cstdio>

#include "sqlite3.h"

#define LOG_TAG "GrimoireNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;
sqlite3* g_db = nullptr;
std::string g_db_path;

struct ScopedStmt {
    sqlite3_stmt* s = nullptr;
    ~ScopedStmt() { if (s) sqlite3_finalize(s); }
    explicit operator bool() const { return s != nullptr; }
};

// ---------- JSON helpers ----------

std::string json_escape(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 16);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof buf, "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

std::string jstr(const std::string& s) { return "\"" + json_escape(s) + "\""; }

std::string col_text(sqlite3_stmt* stmt, int col) {
    const unsigned char* t = sqlite3_column_text(stmt, col);
    return t ? reinterpret_cast<const char*>(t) : "";
}

// ---------- Query sanitization ----------

// Mirror of api.literal_fts(): quote each word containing searchable chars.
std::string literal_fts(const std::string& term) {
    std::istringstream ss(term);
    std::string word, out;
    bool first = true;
    while (ss >> word) {
        bool has_searchable = false;
        for (unsigned char c : word) {
            if (std::isalnum(c) || c >= 0xa1) { has_searchable = true; break; }
        }
        if (!has_searchable) continue;
        std::string escaped;
        escaped.reserve(word.size() * 2);
        for (char c : word) {
            if (c == '"') escaped += "\"\"";
            else escaped += c;
        }
        if (!first) out += " AND ";
        out += "\"" + escaped + "\"";
        first = false;
    }
    return out;
}

// Try the raw term as FTS5; return it if valid, else the literal fallback.
// Returns empty string when no searchable words at all.
std::string fts_query(sqlite3* db, const std::string& term) {
    // Quick validity check with an empty limited query.
    {
        ScopedStmt test;
        const char* sql = "SELECT 1 FROM fts WHERE fts MATCH ? LIMIT 1";
        if (sqlite3_prepare_v2(db, sql, -1, &test.s, nullptr) == SQLITE_OK) {
            sqlite3_bind_text(test.s, 1, term.c_str(), -1, SQLITE_TRANSIENT);
            int rc = sqlite3_step(test.s);
            if (rc != SQLITE_ERROR && rc != SQLITE_MISUSE) {
                return term;
            }
        }
    }
    std::string safe = literal_fts(term);
    return safe;
}

// ---------- DB lifecycle ----------

bool ensure_db_open(const std::string& path) {
    if (g_db && g_db_path == path) return true;
    if (g_db) {
        sqlite3_close_v2(g_db);
        g_db = nullptr;
        g_db_path.clear();
    }
    std::string uri = "file://" + path + "?mode=ro";
    sqlite3* db = nullptr;
    int rc = sqlite3_open_v2(uri.c_str(), &db, SQLITE_OPEN_READONLY | SQLITE_OPEN_URI, nullptr);
    if (rc != SQLITE_OK) {
        LOGE("open failed (%d): %s", rc, db ? sqlite3_errmsg(db) : "unknown");
        if (db) sqlite3_close_v2(db);
        return false;
    }
    // Prove the FTS index is readable and non-empty.
    ScopedStmt check;
    if (sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM fts", -1, &check.s, nullptr) != SQLITE_OK ||
        sqlite3_step(check.s) != SQLITE_ROW) {
        LOGE("fts integrity check failed: %s", sqlite3_errmsg(db));
        sqlite3_close_v2(db);
        return false;
    }
    g_db = db;
    g_db_path = path;
    LOGI("opened db at %s", path.c_str());
    return true;
}

// ---------- Search ----------

// bm25() cannot be used in the same select as a window function, so the
// page of results and the total count are fetched as two queries.
const char* kSearchSql =
    "SELECT title, speaker, source, source_slug,"
    "       snippet(fts, 1, '[', ']', '…', 25)"
    " FROM fts"
    " WHERE fts MATCH ?"
    "   AND (? = '' OR source = ?)"
    " ORDER BY bm25(fts, 3.0, 1.0, 1.0, 1.0, 0.0)"
    "   + CASE WHEN source = 'item' THEN 1.0 ELSE 0 END"
    "   + CASE WHEN speaker = 'Narration' THEN 2.0"
    "          WHEN speaker = 'Cutscene' THEN 1.0 ELSE 0 END"
    " LIMIT ? OFFSET ?";

const char* kCountSql =
    "SELECT COUNT(*) FROM fts WHERE fts MATCH ?"
    "   AND (? = '' OR source = ?)";

std::string run_search(const std::string& query, const std::string& source_filter,
                       int limit, int offset) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto t0 = std::chrono::steady_clock::now();

    std::string safe = fts_query(g_db, query);
    if (safe.empty()) {
        return "{\"error\":\"query has no searchable words\",\"results\":[],\"total\":0}";
    }

    ScopedStmt stmt;
    if (sqlite3_prepare_v2(g_db, kSearchSql, -1, &stmt.s, nullptr) != SQLITE_OK) {
        return std::string("{\"error\":") + jstr(sqlite3_errmsg(g_db)) +
               ",\"results\":[],\"total\":0}";
    }
    sqlite3_bind_text(stmt.s, 1, safe.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt.s, 2, source_filter.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt.s, 3, source_filter.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt.s, 4, limit);
    sqlite3_bind_int(stmt.s, 5, offset);

    std::string results;
    results.reserve(8192);
    bool first = true;
    results += '[';
    while (sqlite3_step(stmt.s) == SQLITE_ROW) {
        std::string title = col_text(stmt.s, 0);
        std::string speaker = col_text(stmt.s, 1);
        std::string source = col_text(stmt.s, 2);
        std::string slug = col_text(stmt.s, 3);
        std::string snippet = col_text(stmt.s, 4);
        if (!first) results += ',';
        first = false;
        results += "{\"title\":" + jstr(title) +
                   ",\"speaker\":" + jstr(speaker) +
                   ",\"source\":" + jstr(source) +
                   ",\"source_slug\":" + jstr(slug) +
                   ",\"snippet\":" + jstr(snippet) + "}";
    }
    results += ']';

    // Total count for the same query (for "N results").
    ScopedStmt count_stmt;
    long total = 0;
    if (sqlite3_prepare_v2(g_db, kCountSql, -1, &count_stmt.s, nullptr) == SQLITE_OK) {
        sqlite3_bind_text(count_stmt.s, 1, safe.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(count_stmt.s, 2, source_filter.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(count_stmt.s, 3, source_filter.c_str(), -1, SQLITE_TRANSIENT);
        if (sqlite3_step(count_stmt.s) == SQLITE_ROW) {
            total = sqlite3_column_int64(count_stmt.s, 0);
        }
    }

    auto t1 = std::chrono::steady_clock::now();
    double elapsed_ms = std::chrono::duration<double, std::milli>(t1 - t0).count();

    std::ostringstream out;
    out << "{\"results\":" << results
        << ",\"total\":" << total
        << ",\"elapsedMs\":" << elapsed_ms
        << ",\"query\":" << jstr(query)
        << "}";
    return out.str();
}

// ---------- Article fetch ----------

// Fetches the full article (all lines/sections) for a search result,
// keyed by the FTS row's source type and source_slug.
//
// Returns: {"found":true, "title":"...", "subtitle":"...", "blocks":[
//   {"label":"Speaker","heading":"","text":"line","stage":false}, ...]}
std::string run_article(const std::string& source, const std::string& slug) {
    std::ostringstream out;
    std::string title, subtitle, kind = source;
    std::vector<std::string> blocks_json;

    auto add_plain = [&](const std::string& text) {
        blocks_json.push_back(
            std::string("{\"label\":\"\",\"heading\":\"\",\"text\":") + jstr(text) + ",\"stage\":false}");
    };
    auto add_labeled = [&](const std::string& label, const std::string& text, bool stage) {
        blocks_json.push_back(
            std::string("{\"label\":") + jstr(label) + ",\"heading\":\"\",\"text\":" + jstr(text) +
            ",\"stage\":" + (stage ? "true" : "false") + "}");
    };
    auto add_heading = [&](const std::string& heading, const std::string& text) {
        blocks_json.push_back(
            std::string("{\"label\":\"\",\"heading\":") + jstr(heading) + ",\"text\":" + jstr(text) +
            ",\"stage\":false}");
    };

    if (source == "transcript_line") {
        { // transcript header
            ScopedStmt s;
            const char* sql = "SELECT title, COALESCE(characters,''), COALESCE(release,'') "
                              "FROM transcripts WHERE slug = ?";
            if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
                sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
                sqlite3_step(s.s) != SQLITE_ROW) {
                return "{\"found\":false}";
            }
            title = col_text(s.s, 0);
            subtitle = col_text(s.s, 1);   // characters
            if (subtitle.empty()) subtitle = col_text(s.s, 2);  // release
        }
        ScopedStmt s;
        const char* sql = "SELECT COALESCE(tl.speaker,''), tl.line, COALESCE(tl.is_stage_dir,0) "
                          "FROM transcript_lines tl JOIN transcripts t ON t.id = tl.transcript_id "
                          "WHERE t.slug = ? ORDER BY tl.id";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK) {
            return "{\"found\":false}";
        }
        sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT);
        while (sqlite3_step(s.s) == SQLITE_ROW) {
            std::string speaker = col_text(s.s, 0);
            std::string line = col_text(s.s, 1);
            bool stage = sqlite3_column_int(s.s, 2) != 0;
            if (stage) {
                add_labeled("stage direction", line, true);
            } else {
                add_labeled(speaker, line, false);
            }
        }
    } else if (source == "book_entry") {
        {
            ScopedStmt s;
            const char* sql = "SELECT title FROM books WHERE slug = ?";
            if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
                sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
                sqlite3_step(s.s) != SQLITE_ROW) {
                return "{\"found\":false}";
            }
            title = col_text(s.s, 0);
        }
        {
            ScopedStmt s;
            const char* sql = "SELECT COUNT(*) FROM book_entries "
                              "WHERE book_id = (SELECT id FROM books WHERE slug = ?)";
            if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) == SQLITE_OK) {
                sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT);
                if (sqlite3_step(s.s) == SQLITE_ROW) {
                    long n = sqlite3_column_int64(s.s, 0);
                    subtitle = std::to_string(n) + (n == 1 ? " section" : " sections");
                }
            }
        }
        ScopedStmt s;
        const char* sql = "SELECT COALESCE(title,''), COALESCE(body,'') FROM book_entries "
                          "WHERE book_id = (SELECT id FROM books WHERE slug = ?) ORDER BY seq";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK) {
            return "{\"found\":false}";
        }
        sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT);
        while (sqlite3_step(s.s) == SQLITE_ROW) {
            std::string heading = col_text(s.s, 0);
            std::string body = col_text(s.s, 1);
            if (heading.empty()) {
                add_plain(body);
            } else {
                add_heading(heading, body);
            }
        }
    } else if (source == "item") {
        ScopedStmt s;
        const char* sql = "SELECT title, COALESCE(text,''), COALESCE(item_type,''), COALESCE(release,'') "
                          "FROM items WHERE slug = ?";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
            sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_step(s.s) != SQLITE_ROW) {
            return "{\"found\":false}";
        }
        title = col_text(s.s, 0);
        subtitle = col_text(s.s, 2);
        std::string text = col_text(s.s, 1);
        std::string release = col_text(s.s, 3);
        if (!subtitle.empty() && !release.empty()) subtitle += " · " + release;
        else if (subtitle.empty()) subtitle = release;
        add_plain(text);
    } else if (source == "interaction") {
        ScopedStmt s;
        const char* sql = "SELECT title, COALESCE(speaker,''), COALESCE(text,''), COALESCE(release,'') "
                          "FROM interactions WHERE slug = ?";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
            sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_step(s.s) != SQLITE_ROW) {
            return "{\"found\":false}";
        }
        title = col_text(s.s, 0);
        std::string speaker = col_text(s.s, 1);
        subtitle = speaker;
        std::string text = col_text(s.s, 2);
        add_labeled(speaker, text, false);
    } else if (source == "lore_entry") {
        ScopedStmt s;
        const char* sql = "SELECT title, COALESCE(subtitle,''), COALESCE(body,'') "
                          "FROM lore_entries WHERE hash = CAST(substr(?, 6) AS INTEGER)";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
            sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_step(s.s) != SQLITE_ROW) {
            return "{\"found\":false}";
        }
        title = col_text(s.s, 0);
        subtitle = col_text(s.s, 1);
        add_plain(col_text(s.s, 2));
    } else if (source == "grimoire_card") {
        ScopedStmt s;
        const char* sql = "SELECT title, COALESCE(body,'') FROM grimoire_cards WHERE slug = ?";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
            sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_step(s.s) != SQLITE_ROW) {
            return "{\"found\":false}";
        }
        title = col_text(s.s, 0);
        subtitle = "Grimoire card · Destiny 1";
        add_plain(col_text(s.s, 1));
    } else if (source == "record") {
        ScopedStmt s;
        const char* sql = "SELECT title, COALESCE(body,''), COALESCE(release,'') FROM records WHERE slug = ?";
        if (sqlite3_prepare_v2(g_db, sql, -1, &s.s, nullptr) != SQLITE_OK ||
            sqlite3_bind_text(s.s, 1, slug.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_step(s.s) != SQLITE_ROW) {
            return "{\"found\":false}";
        }
        title = col_text(s.s, 0);
        subtitle = col_text(s.s, 2);
        add_plain(col_text(s.s, 1));
    } else {
        return "{\"found\":false}";
    }

    out << "{\"found\":true,\"kind\":" << jstr(kind)
        << ",\"title\":" << jstr(title)
        << ",\"subtitle\":" << jstr(subtitle)
        << ",\"blocks\":[";
    for (size_t i = 0; i < blocks_json.size(); ++i) {
        if (i) out << ',';
        out << blocks_json[i];
    }
    out << "]}";
    return out.str();
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_grimoire_android_native_GrimoireNative_nativeOpenDb(
        JNIEnv* env, jobject /*thiz*/, jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = ensure_db_open(path ? path : "");
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? env->NewStringUTF("ok") : env->NewStringUTF("");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_grimoire_android_native_GrimoireNative_nativeIsDbOpen(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_db != nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_grimoire_android_native_GrimoireNative_nativeDbInfo(
        JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::ostringstream out;
    if (!g_db) {
        return env->NewStringUTF("{\"ready\":false}");
    }
    long rows = 0;
    ScopedStmt stmt;
    if (sqlite3_prepare_v2(g_db, "SELECT COUNT(*) FROM fts", -1, &stmt.s, nullptr) == SQLITE_OK &&
        sqlite3_step(stmt.s) == SQLITE_ROW) {
        rows = sqlite3_column_int64(stmt.s, 0);
    }
    out << "{\"ready\":true,\"path\":" << jstr(g_db_path)
        << ",\"rows\":" << rows << "}";
    return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_grimoire_android_native_GrimoireNative_nativeSearch(
        JNIEnv* env, jobject /*thiz*/, jstring jquery, jstring jsource, jint limit, jint offset) {
    const char* q = env->GetStringUTFChars(jquery, nullptr);
    const char* src = env->GetStringUTFChars(jsource, nullptr);
    std::string query = q ? q : "";
    std::string source = src ? src : "";
    env->ReleaseStringUTFChars(jquery, q);
    env->ReleaseStringUTFChars(jsource, src);

    if (limit <= 0 || limit > 100) limit = 20;
    if (offset < 0) offset = 0;

    std::string result = run_search(query, source, limit, offset);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_grimoire_android_native_GrimoireNative_nativeClose(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_db) {
        sqlite3_close_v2(g_db);
        g_db = nullptr;
        g_db_path.clear();
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_grimoire_android_native_GrimoireNative_nativeArticle(
        JNIEnv* env, jobject /*thiz*/, jstring jsource, jstring jslug) {
    const char* s = env->GetStringUTFChars(jsource, nullptr);
    const char* sl = env->GetStringUTFChars(jslug, nullptr);
    std::string source = s ? s : "";
    std::string slug = sl ? sl : "";
    env->ReleaseStringUTFChars(jsource, s);
    env->ReleaseStringUTFChars(jslug, sl);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_db) {
        return env->NewStringUTF("{\"found\":false,\"error\":\"database not open\"}");
    }
    return env->NewStringUTF(run_article(source, slug).c_str());
}
