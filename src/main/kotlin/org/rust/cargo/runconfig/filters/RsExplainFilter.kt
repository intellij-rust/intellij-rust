/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.execution.filters.BrowserHyperlinkInfo
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.DumbAware
import org.rust.ide.RsConstants
import java.util.regex.Pattern


/**
 * Filters output for “[--explain Exxxx]” (or other similar patterns) and links
 * to the relevant documentation.
 */
class RsExplainFilter : Filter, DumbAware {
    private val patterns = listOf(
        Pattern.compile("--explain E(\\d{4})"),
        Pattern.compile("(error|warning)\\[E(\\d{4})\\]"))

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = patterns
            .map { it.matcher(line) }
            .firstOrNull { it.find() } ?: return null

        val (offset, length, code) = when (matcher.groupCount()) {
            1 -> Triple(0, matcher.group(0).length, matcher.group(1))
            else -> Triple(matcher.group(1).length, matcher.group(2).length + 3, matcher.group(2))
        }
        val url = "${RsConstants.ERROR_INDEX_URL}#E$code"
        val info = BrowserHyperlinkInfo(url)

        val startOffset = entireLength - line.length + matcher.start() + offset
        val endOffset = startOffset + length

        return Filter.Result(startOffset, endOffset, info)
    }
}
