package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RustReference

interface RsReferenceElement : RsCompositeElement {

    val referenceNameElement: PsiElement

    val referenceName: String

    override fun getReference(): RustReference
}
