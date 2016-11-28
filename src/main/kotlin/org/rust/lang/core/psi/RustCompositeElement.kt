package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.resolve.ref.RustReference

interface RustCompositeElement : PsiElement {
    override fun getReference(): RustReference?
}

val RustCompositeElement.containingMod: RustMod?
    get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RustMod::class.java)
