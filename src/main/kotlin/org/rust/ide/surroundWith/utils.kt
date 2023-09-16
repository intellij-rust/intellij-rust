/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsPsiFactory

/**
 * Shortcut for [PsiElement.addRangeAfter]
 */
fun RsBlock.addStatements(statements: Array<out PsiElement>) {
    val factory = RsPsiFactory(project)
    addBefore(factory.createWhitespace("\n    "), rbrace)
    addRangeBefore(statements.first(), statements.last(), rbrace)
    addBefore(factory.createNewline(), rbrace)
}

fun RsBlock.addStatement(statement: PsiElement) {
    val newline = RsPsiFactory(project).createNewline()
    if ((rbrace?.prevSibling as? PsiWhiteSpace)?.textContains('\n') != true) addBefore(newline, rbrace)
    addBefore(statement, rbrace)
    addBefore(newline, rbrace)
}
