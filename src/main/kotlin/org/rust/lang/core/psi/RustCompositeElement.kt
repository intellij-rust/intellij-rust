package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference

interface RustCompositeElement   : PsiElement
                                 , NavigatablePsiElement {

    override fun getReference(): RustReference?

}

val RustCompositeElement.containingMod: RustMod?
    get() = parentOfType()
