/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.google.common.annotations.VisibleForTesting
import com.intellij.lang.PsiBuilder
import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import org.rust.lang.core.macros.decl.MacroExpansionMarks
import org.rust.lang.core.parser.createAdaptedRustPsiBuilder
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.lang.core.parser.rawLookupText
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.doc.psi.RsDocKind
import org.rust.lang.utils.escapeRust

fun PsiBuilder.lowerDocCommentsToAdaptedPsiBuilder(project: Project): Pair<PsiBuilder, RangeMap> {
    val lowered = lowerDocComments() ?: return this to defaultRangeMap()
    return project.createAdaptedRustPsiBuilder(lowered.first) to lowered.second
}

fun PsiBuilder.lowerDocCommentsToPsiBuilder(project: Project): Pair<PsiBuilder, RangeMap> {
    val lowered = lowerDocComments() ?: return this to defaultRangeMap()
    return project.createRustPsiBuilder(lowered.first) to lowered.second
}

private fun PsiBuilder.defaultRangeMap(): RangeMap= if (originalText.isNotEmpty()) {
    RangeMap(MappedTextRange(0, 0, originalText.length))
} else {
    RangeMap.EMPTY
}

/** Rustc replaces doc comments like `/// foo` to attributes `#[doc = "foo"]` before macro expansion */
@VisibleForTesting
fun PsiBuilder.lowerDocComments(): Pair<CharSequence, RangeMap>? {
    if (!hasDocComments()) {
        return null
    }

    MacroExpansionMarks.DocsLowering.hit()

    val sb = StringBuilder((originalText.length * 1.1).toInt())
    val ranges = SmartList<MappedTextRange>()

    var i = 0
    while (true) {
        val token = rawLookup(i) ?: break
        val text = rawLookupText(i)
        val start = rawTokenTypeStart(i)
        i++

        if (token in RS_DOC_COMMENTS) {
            val kind = RsDocKind.of(token)
            val attrPrefix = if (kind == RsDocKind.InnerBlock || kind == RsDocKind.InnerEol) {
                "#!"
            } else {
                "#"
            }
            if (kind.isBlock) {
                sb.append(attrPrefix)
                sb.append("[doc=\"")
                text.removePrefix(kind.prefix).removeSuffix(kind.suffix).escapeRust(sb)
                sb.append("\"]\n")
            } else {
                for (comment in text.splitToSequence("\n")) {
                    sb.append(attrPrefix)
                    sb.append("[doc=\"")
                    comment.trimStart().removePrefix(kind.prefix).escapeRust(sb)
                    sb.append("\"]\n")
                }
            }
        } else {
            ranges.mergeAdd(MappedTextRange(start, sb.length, text.length))
            sb.append(text)
        }
    }

    return sb to RangeMap(ranges)
}

private fun PsiBuilder.hasDocComments(): Boolean {
    var i = 0
    while (true) {
        val token = rawLookup(i++) ?: break
        if (token in RS_DOC_COMMENTS) return true
    }
    return false
}
