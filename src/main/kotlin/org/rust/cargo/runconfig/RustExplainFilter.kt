package org.rust.cargo.runconfig

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.project.DumbAware
import java.util.regex.Pattern


/**
 * Filters output for “[--explain Exxxx]” and links to the relevant
 * documentation.
 */
class RustExplainFilter : Filter, DumbAware {
    private val link_length: Int = 15
    private val pattern: Pattern = Pattern.compile("--explain E(\\d{4})")

    override fun applyFilter(line: String, entireLength: Int): Result? {
        val matcher = pattern.matcher(line)
        if (!matcher.find()) {
            return null
        }

        val eNumber = matcher.group(1)
        val url = "https://doc.rust-lang.org/error-index.html#E$eNumber"
        val info = OpenUrlHyperlinkInfo(url)

        val highlightStartOffset = entireLength - line.length + matcher.start()
        val highlightEndOffset = highlightStartOffset + link_length

        return Result(highlightStartOffset, highlightEndOffset, info)
    }
}
