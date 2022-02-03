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
    return project.createAdaptedRustPsiBuilder(lowered.text) to lowered.ranges
}

fun PsiBuilder.lowerDocCommentsToPsiBuilder(project: Project): Pair<PsiBuilder, RangeMap> {
    val lowered = lowerDocComments() ?: return this to defaultRangeMap()
    return project.createRustPsiBuilder(lowered.text) to lowered.ranges
}

private fun PsiBuilder.defaultRangeMap(): RangeMap= if (originalText.isNotEmpty()) {
    RangeMap.from(SmartList(MappedTextRange(0, 0, originalText.length)))
} else {
    RangeMap.EMPTY
}

/** Rustc replaces doc comments like `/// foo` to attributes `#[doc = "foo"]` before macro expansion */
@VisibleForTesting
fun PsiBuilder.lowerDocComments(): MappedText? {
    if (!hasDocComments()) {
        return null
    }

    MacroExpansionMarks.docsLowering.hit()

    val result = MutableMappedText((originalText.length * 1.1).toInt())

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
                result.appendUnmapped(attrPrefix)
                result.appendUnmapped("[doc=\"")
                text.removePrefix(kind.prefix)
                    .removeSuffix(kind.suffix)
                    .escapeRust(result.withSrcOffset(start + kind.prefix.length))
                result.appendUnmapped("\"]\n")
            } else {
                var startOfLine = start
                for (comment in text.splitToSequence("\n")) {
                    result.appendUnmapped(attrPrefix)
                    result.appendUnmapped("[doc=\"")

                    val commentTrimmed = comment.trimStart()
                    val indentLength = comment.length - commentTrimmed.length
                    commentTrimmed
                        .removePrefix(kind.prefix)
                        .escapeRust(result.withSrcOffset(startOfLine + indentLength + kind.prefix.length))
                    startOfLine += comment.length + 1

                    result.appendUnmapped("\"]\n")
                }
            }
        } else {
            result.appendMapped(text, start)
        }
    }

    return result.toMappedText()
}

private fun PsiBuilder.hasDocComments(): Boolean {
    var i = 0
    while (true) {
        val token = rawLookup(i++) ?: break
        if (token in RS_DOC_COMMENTS) return true
    }
    return false
}
