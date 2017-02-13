package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.impl.RsCompositeElementImpl
import org.rust.lang.core.resolve.ref.RsLifetimeReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.refactoring.RsNamesValidator

abstract class RsLifetimeImplMixin (node: ASTNode) : RsCompositeElementImpl(node), RsLifetime {

    override val referenceNameElement: PsiElement get() = quoteIdentifier

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsLifetimeReferenceImpl(this)

}

val RsLifetime.isPredefined : Boolean get() = quoteIdentifier.text in RsNamesValidator.PredefinedLifetimes
