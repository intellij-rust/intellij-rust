package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RustReference

interface RustReferenceElement : RustCompositeElement {

    val referenceNameElement: PsiElement

    override fun getReference(): RustReference
}

val RustReferenceElement.referenceName: String
    get() = referenceNameElement.text
