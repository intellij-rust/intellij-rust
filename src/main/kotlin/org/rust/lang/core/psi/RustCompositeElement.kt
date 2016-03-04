package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference

interface RustCompositeElement   : PsiElement
                                 , NavigatablePsiElement
                                 , RustTokenElementTypes /* This is actually a hack to overcome GK limitations */ {
    override fun getReference(): RustReference?
}

val RustCompositeElement.containingMod: RustModItem?
    get() = parentOfType()
