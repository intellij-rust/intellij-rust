package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustTypeItemElement

class RustTypeTreeElement(element: RustTypeItemElement) : PsiTreeElementBase<RustTypeItemElement>(element) {
    override fun getPresentableText(): String? = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()
}
