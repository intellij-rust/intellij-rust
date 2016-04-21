package org.rust.lang.core.psi.impl

import com.intellij.psi.PsiElement

// The only use of this interface is to be extended by RustBinaryExpr
internal interface RustBinaryExprExternalTokens {
    fun getGtgteq(): PsiElement?
    fun getGtgt(): PsiElement?
    fun getGteq(): PsiElement?
    fun getLtlteq(): PsiElement?
    fun getLtlt(): PsiElement?
    fun getLteq(): PsiElement?
    fun getOror(): PsiElement?
}
