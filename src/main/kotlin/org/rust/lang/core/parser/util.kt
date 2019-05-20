/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.project.Project
import org.rust.lang.RsLanguage

fun Project.createRustPsiBuilder(text: String): PsiBuilder {
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(RsLanguage)
        ?: error("No parser definition for language $RsLanguage")
    val lexer = parserDefinition.createLexer(this)
    return PsiBuilderFactory.getInstance().createBuilder(parserDefinition, lexer, text)
}

inline fun <T> PsiBuilder.probe(action: () -> T): T {
    val mark = mark()
    try {
        return action()
    } finally {
        mark.rollbackTo()
    }
}
