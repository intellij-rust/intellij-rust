package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.rust.lang.core.psi.RustCompositeElement

interface RustReference : PsiPolyVariantReference {

    override fun getElement(): RustCompositeElement

    override fun resolve(): RustCompositeElement?

    fun multiResolve(): List<RustCompositeElement>
}


