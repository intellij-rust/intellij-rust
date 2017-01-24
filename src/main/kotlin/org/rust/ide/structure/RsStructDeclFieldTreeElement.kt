package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsFieldDecl

class RsStructDeclFieldTreeElement(element: RsFieldDecl) : PsiTreeElementBase<RsFieldDecl>(element) {

    override fun getPresentableText() = "${element?.identifier?.text}: ${element?.typeReference?.text}"

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
