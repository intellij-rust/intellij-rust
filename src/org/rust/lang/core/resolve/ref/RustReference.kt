package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiReference
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement

interface RustReference : PsiReference {

    override fun getElement(): RustCompositeElement

    override fun resolve(): RustNamedElement?
}


