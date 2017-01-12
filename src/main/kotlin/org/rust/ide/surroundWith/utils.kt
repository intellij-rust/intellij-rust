package org.rust.ide.surroundWith

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBlock

/**
 * Shortcut for [PsiElement.addRangeAfter]
 */
fun RsBlock.addStatements(statements: Array<out PsiElement>): PsiElement {
    return addRangeAfter(statements.first(), statements.last(), lbrace)
}
