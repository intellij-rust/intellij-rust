package org.rust.lang.core.psi

import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

interface RustLiteral : PsiElement, NavigationItem {
    val tokenType: IElementType
    val value: Any?
    val valueString: String
    val suffix: String
    val possibleSuffixes: Collection<String>
    val hasPairedDelimiters: Boolean
}
