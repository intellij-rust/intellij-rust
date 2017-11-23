/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith

import com.intellij.psi.PsiElement
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
