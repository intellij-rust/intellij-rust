package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.RustStructDeclField

class RustStructDeclFieldTreeElement(element: RustStructDeclField) : PsiTreeElementBase<RustStructDeclField>(element) {

    override fun getPresentableText() = "${element?.identifier?.text}: ${element?.typeSum?.text}"

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
