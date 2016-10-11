package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement

interface RustReference : PsiPolyVariantReference {

    override fun getElement(): RustCompositeElement

    override fun resolve(): RustNamedElement?

    fun multiResolve(): List<RustNamedElement>
}


