package org.rust.cargo.runconfig

import com.intellij.execution.filters.BrowserHyperlinkInfo
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.project.DumbAware
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * Filters output for “[--explain Exxxx]” (or other similar patterns) and links
 * to the relevant documentation.
 */
class RustExplainFilter : Filter, DumbAware {
    private val patterns = listOf(
        Pattern.compile("--explain E(\\d{4})"),
        Pattern.compile("error\\[E(\\d{4})\\]"))

    override fun applyFilter(line: String, entireLength: Int): Result? {
        val matcher = patterns
            .map { it.matcher(line) }
            .firstOrNull { it.find() } ?: return null

        val eNumber = matcher.group(1)
        val url = "https://doc.rust-lang.org/error-index.html#E$eNumber"
        val info = BrowserHyperlinkInfo(url)

        val highlightStartOffset = entireLength - line.length + matcher.start()
        val highlightEndOffset = highlightStartOffset + matcher.group().length

        return Result(highlightStartOffset, highlightEndOffset, info)
    }
}
