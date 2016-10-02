package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustConstItemElement

class RustConstTreeElement(element: RustConstItemElement) : PsiTreeElementBase<RustConstItemElement>(element) {

    override fun getPresentableText() = "${element?.name}: ${element?.type?.text}"

    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()
}
