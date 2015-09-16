package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiQualifiedReferenceElement
import org.rust.lang.core.psi.RustNamedElement

public interface RustQualifiedReference : RustNamedElement
                                        , PsiQualifiedReferenceElement {

    fun getSeparator(): PsiElement?

    fun getReferenceNameElement(): PsiElement?

    override fun getQualifier() : RustQualifiedReference?

}