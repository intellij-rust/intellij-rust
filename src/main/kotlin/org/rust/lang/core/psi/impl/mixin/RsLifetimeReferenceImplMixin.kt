package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetimeReference
import org.rust.lang.core.psi.impl.RsCompositeElementImpl
import org.rust.lang.core.resolve.ref.RsLifetimeReferenceReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsLifetimeReferenceImplMixin (node: ASTNode) : RsCompositeElementImpl(node), RsLifetimeReference {

    override val referenceNameElement: PsiElement get() = lifetime

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsLifetimeReferenceReferenceImpl(this)

}
