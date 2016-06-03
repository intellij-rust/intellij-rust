package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustFieldDeclElement

class RustStructDeclFieldTreeElement(element: RustFieldDeclElement) : PsiTreeElementBase<RustFieldDeclElement>(element) {

    override fun getPresentableText() = "${element?.identifier?.text}: ${element?.type?.text}"

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
