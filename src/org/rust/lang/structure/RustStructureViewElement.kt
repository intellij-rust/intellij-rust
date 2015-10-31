package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.NavigatablePsiElement

class RustStructureViewElement(element: NavigatablePsiElement) : PsiTreeElementBase<NavigatablePsiElement>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
