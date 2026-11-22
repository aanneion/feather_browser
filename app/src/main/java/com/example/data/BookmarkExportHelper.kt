package com.example.data

import com.example.data.model.Bookmark
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

object BookmarkExportHelper {

    fun exportToNetscapeHtml(bookmarks: List<Bookmark>, profileName: String = "Feather"): String {
        val sb = StringBuilder()
        val nowSec = System.currentTimeMillis() / 1000

        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file. It will be read and overwritten. Do Not Edit! -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>Bookmarks</H1>\n")
        sb.append("<DL><p>\n")
        val grouped = bookmarks.groupBy { it.folderName.ifBlank { "Mobile Bookmarks" } }
        if (grouped.isEmpty()) {
            sb.append("    <DT><H3 ADD_DATE=\"$nowSec\" LAST_MODIFIED=\"$nowSec\">")
            sb.append(escapeHtml(profileName)).append(" Bookmarks</H3>\n")
            sb.append("    <DL><p>\n    </DL><p>\n")
        } else {
            for ((folder, items) in grouped) {
                val folderLabel = if (folder.equals("Mobile Bookmarks", ignoreCase = true) && grouped.size == 1) {
                    "$profileName Bookmarks"
                } else {
                    folder
                }
                sb.append("    <DT><H3 ADD_DATE=\"$nowSec\" LAST_MODIFIED=\"$nowSec\">")
                sb.append(escapeHtml(folderLabel)).append("</H3>\n")
                sb.append("    <DL><p>\n")
                for (bm in items) {
                    val addDate = if (bm.createdAt > 0) bm.createdAt / 1000 else nowSec
                    val safeUrl = escapeHtml(bm.url)
                    val safeTitle = escapeHtml(if (bm.title.isNotBlank()) bm.title else bm.url)
                    sb.append("        <DT><A HREF=\"").append(safeUrl)
                        .append("\" ADD_DATE=\"").append(addDate)
                        .append("\">").append(safeTitle).append("</A>\n")
                }
                sb.append("    </DL><p>\n")
            }
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    fun parseBookmarks(rawContent: String, targetProfileId: String): List<Bookmark> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Try parsing JSON if content starts with [ or {
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val jsonResults = parseJsonBookmarks(trimmed, targetProfileId)
            if (jsonResults.isNotEmpty()) return jsonResults
        }

        // 2. Parse Netscape HTML / Standard Anchor tags
        return parseHtmlBookmarks(trimmed, targetProfileId)
    }

    private fun parseHtmlBookmarks(html: String, targetProfileId: String): List<Bookmark> {
        val results = mutableListOf<Bookmark>()
        val tokenPattern = Pattern.compile(
            "<h3\\b[^>]*>(.*?)<\\/h3>|<\\/dl>|<a\\s+[^>]*?href=[\"']([^\"']+)[\"'][^>]*>(.*?)<\\/a>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = tokenPattern.matcher(html)

        val folderStack = ArrayDeque<String>()
        val seenUrls = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        while (matcher.find()) {
            val rawHeading = matcher.group(1)
            val rawUrl = matcher.group(2)
            val rawTitle = matcher.group(3)

            if (rawHeading != null) {
                val folderTitle = unescapeHtml(rawHeading.replace(Regex("<[^>]*>"), "")).trim()
                if (folderTitle.isNotBlank()) {
                    folderStack.addLast(folderTitle)
                }
                continue
            }

            val fullToken = matcher.group(0) ?: ""
            if (fullToken.startsWith("</dl", ignoreCase = true)) {
                if (folderStack.isNotEmpty()) {
                    folderStack.removeLast()
                }
                continue
            }

            val cleanUrl = rawUrl?.trim().orEmpty()
            if (cleanUrl.isBlank() || cleanUrl.startsWith("javascript:", ignoreCase = true)) {
                continue
            }

            val unescapedUrl = unescapeHtml(cleanUrl)
            val unescapedTitle = unescapeHtml((rawTitle ?: "").replace(Regex("<[^>]*>"), "")).trim().ifBlank { unescapedUrl }
            val activeFolder = folderStack.lastOrNull()
                ?.takeUnless { it.endsWith(" Bookmarks", ignoreCase = true) || it.equals("Bookmarks", ignoreCase = true) }
                ?: "Mobile Bookmarks"

            if (seenUrls.add(unescapedUrl)) {
                results.add(
                    Bookmark(
                        profileId = targetProfileId,
                        folderName = activeFolder,
                        title = unescapedTitle,
                        url = unescapedUrl,
                        createdAt = now
                    )
                )
            }
        }
        return results
    }

    private fun parseJsonBookmarks(jsonStr: String, targetProfileId: String): List<Bookmark> {
        val results = mutableListOf<Bookmark>()
        val seenUrls = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        try {
            val array = if (jsonStr.startsWith("[")) {
                JSONArray(jsonStr)
            } else {
                val obj = JSONObject(jsonStr)
                obj.optJSONArray("bookmarks") ?: obj.optJSONArray("items") ?: JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url", "").trim()
                val title = item.optString("title", "").ifBlank { url }
                val folder = item.optString("folder", item.optString("folderName", "Mobile Bookmarks"))
                    .trim()
                    .ifBlank { "Mobile Bookmarks" }
                if (url.isNotBlank() && !url.startsWith("javascript:", ignoreCase = true) && seenUrls.add(url)) {
                    results.add(
                        Bookmark(
                            profileId = targetProfileId,
                            folderName = folder,
                            title = title,
                            url = url,
                            createdAt = now
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Silently fallback if JSON malformed
        }
        return results
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }
}
