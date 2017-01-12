package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.rust.lang.core.psi.RsCompositeElement

interface RustReference : PsiPolyVariantReference {

    override fun getElement(): RsCompositeElement

    override fun resolve(): RsCompositeElement?

    fun multiResolve(): List<RsCompositeElement>
}


