package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RustReference

interface RustQualifiedReferenceElement : RustNamedElement {

    val isFullyQualified: Boolean

    val separator: PsiElement?

    val qualifier: RustQualifiedReferenceElement?

    override val nameElement: PsiElement?

    override fun getReference(): RustReference
}
