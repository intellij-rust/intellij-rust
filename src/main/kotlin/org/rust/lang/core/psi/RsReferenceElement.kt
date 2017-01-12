package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RsReference

interface RsReferenceElement : RsCompositeElement {

    val referenceNameElement: PsiElement

    val referenceName: String

    override fun getReference(): RsReference
}
