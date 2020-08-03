/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.project.Project
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes

fun Project.createRustPsiBuilder(text: CharSequence): PsiBuilder {
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(RsLanguage)
        ?: error("No parser definition for language $RsLanguage")
    val lexer = parserDefinition.createLexer(this)
    return PsiBuilderFactory.getInstance().createBuilder(parserDefinition, lexer, text)
}

/** Creates [PsiBuilder] suitable for Grammar Kit generated methods */
fun Project.createAdaptedRustPsiBuilder(text: CharSequence): PsiBuilder {
    val b = GeneratedParserUtilBase.adapt_builder_(
        RsElementTypes.FUNCTION,
        createRustPsiBuilder(text),
        RustParser(),
        RustParser.EXTENDS_SETS_
    )
    // Equivalent to `GeneratedParserUtilBase.enter_section_`.
    // Allows to call `RustParser.*` methods without entering the section
    GeneratedParserUtilBase.ErrorState.get(b).currentFrame = GeneratedParserUtilBase.Frame()
    return b
}

inline fun <T> PsiBuilder.probe(action: () -> T): T {
    val mark = mark()
    try {
        return action()
    } finally {
        mark.rollbackTo()
    }
}

fun PsiBuilder.rawLookupText(steps: Int): CharSequence {
    val start = rawTokenTypeStart(steps)
    val end = rawTokenTypeStart(steps + 1)
    return if (start == -1 || end == -1) "" else originalText.subSequence(start, end)
}

fun PsiBuilder.Marker.close(result: Boolean): Boolean {
    if (result) {
        drop()
    } else {
        rollbackTo()
    }
    return result
}

fun PsiBuilder.clearFrame() {
    val state = GeneratedParserUtilBase.ErrorState.get(this)
    val currentFrame = state.currentFrame
    if (currentFrame != null) {
        currentFrame.errorReportedAt = -1
        currentFrame.lastVariantAt = -1
    }
}
