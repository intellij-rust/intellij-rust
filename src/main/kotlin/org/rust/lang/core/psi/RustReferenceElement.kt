package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RustReference

interface RustReferenceElement : RustNamedElement {
    val nameElement: PsiElement

    override fun getReference(): RustReference
}
