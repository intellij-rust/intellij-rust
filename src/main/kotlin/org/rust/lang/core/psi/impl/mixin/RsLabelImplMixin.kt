package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.impl.RsCompositeElementImpl
import org.rust.lang.core.resolve.ref.RsLabelReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsLabelImplMixin (node: ASTNode) : RsCompositeElementImpl(node), RsLabel {

    override val referenceNameElement: PsiElement get() = lifetime

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsLabelReferenceImpl(this)

}
