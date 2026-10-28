package com.trailstatus.dfw

/** Parses the Trailforks "region_list" widget table that dorba.org embeds. */
object TrailforksParser {
    private val rowRe = Regex("""<tr\b[^>]*>([\s\S]*?)</tr>""")
    private val iconRe = Regex("""<span\b[^>]*\bsicon_small\b[^>]*>""")
    private val linkRe = Regex("""<a\b([^>]*)>([^<]+)</a>""")
    private val timeRe = Regex("""<div\b[^>]*\bfullTime\b[^>]*>""")
    private val colorRe = Regex("""\bs(green|yellow|amber|orange|red)\b""")

    fun parse(html: String): List<Trail> = rowRe.findAll(html).mapNotNull { m ->
        val row = m.groupValues[1]
        val icon = iconRe.find(row)?.value ?: return@mapNotNull null
        val link = linkRe.find(row) ?: return@mapNotNull null

        val colorClass = attr(icon, "class")?.let { colorRe.find(it)?.groupValues?.get(1) }
        val level = StatusLevel.fromCode(attr(icon, "data-sort")?.toIntOrNull())
            ?: StatusLevel.fromColorClass(colorClass)
        val updated = timeRe.find(row)?.value?.let { attr(it, "data-sort")?.toLongOrNull() }

        Trail(
            name = decode(link.groupValues[2].trim()),
            url = attr(link.groupValues[1], "href"),
            level = level,
            statusText = attr(icon, "title")?.let(::decode) ?: level.label,
            updatedEpochSec = updated,
        )
    }.toList()

    private fun attr(tag: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}="([^"]*)"""").find(tag)?.groupValues?.get(1)

    private fun decode(s: String) = s
        .replace("&amp;", "&")
        .replace("&#039;", "'").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
