package org.rust.ide.surroundWith

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement

/**
 * Shortcut for [PsiElement.addRangeAfter]
 */
fun RustBlockElement.addStatements(statements: Array<out PsiElement>): PsiElement {
    return addRangeAfter(statements.first(), statements.last(), lbrace)
}
