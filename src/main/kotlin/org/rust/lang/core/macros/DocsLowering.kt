/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.rawLookupText
import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import org.rust.lang.core.macros.decl.MacroExpansionMarks
import org.rust.lang.core.parser.createAdaptedRustPsiBuilder
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.doc.psi.RsDocKind

fun PsiBuilder.lowerDocCommentsToAdaptedPsiBuilder(project: Project): Pair<PsiBuilder, RangeMap> {
    val lowered = lowerDocComments() ?: return this to defaultRangeMap()
    return project.createAdaptedRustPsiBuilder(lowered.first) to lowered.second
}

fun PsiBuilder.lowerDocCommentsToPsiBuilder(project: Project): Pair<PsiBuilder, RangeMap> {
    val lowered = lowerDocComments() ?: return this to defaultRangeMap()
    return project.createRustPsiBuilder(lowered.first) to lowered.second
}

private fun PsiBuilder.defaultRangeMap(): RangeMap= if (originalText.isNotEmpty()) {
    RangeMap.from(SmartList(MappedTextRange(0, 0, originalText.length)))
} else {
    RangeMap.EMPTY
}

/** Rustc replaces doc comments like `/// foo` to attributes `#[doc = "foo"]` before macro expansion */
private fun PsiBuilder.lowerDocComments(): Pair<CharSequence, RangeMap>? {
    if (!hasDocComments()) {
        return null
    }

    MacroExpansionMarks.docsLowering.hit()

    val sb = StringBuilder((originalText.length * 1.1).toInt())
    val ranges = SmartList<MappedTextRange>()

    var i = 0
    while (true) {
        val token = rawLookup(i) ?: break
        val text = rawLookupText(i)
        val start = rawTokenTypeStart(i)
        i++

        if (token in RS_DOC_COMMENTS) {
            // TODO calculate how many `#` we should insert
            sb.append("#[doc=r###\"")
            RsDocKind.of(token).removeDecoration(text.splitToSequence("\n")).joinTo(sb, separator = "\n")
            sb.append("\"###]")
        } else {
            ranges.mergeAdd(MappedTextRange(start, sb.length, text.length))
            sb.append(text)
        }
    }

    return sb to RangeMap.from(ranges)
}

private fun PsiBuilder.hasDocComments(): Boolean {
    var i = 0
    while (true) {
        val token = rawLookup(i++) ?: break
        if (token in RS_DOC_COMMENTS) return true
    }
    return false
}
