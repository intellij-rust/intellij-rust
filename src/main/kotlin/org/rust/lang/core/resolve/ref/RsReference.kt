package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.types.BoundElement

interface RsReference : PsiPolyVariantReference {

    override fun getElement(): RsCompositeElement

    override fun resolve(): RsCompositeElement?

    fun advancedResolve(): BoundElement<RsCompositeElement>? = resolve()?.let { BoundElement(it) }

    fun multiResolve(): List<RsCompositeElement>
}


