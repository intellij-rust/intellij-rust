package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructItem

class RustStructTreeElement(element: RustStructItem) : PsiTreeElementBase<RustStructItem>(element) {

    override fun getPresentableText(): String? = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        return arrayListOf<StructureViewTreeElement>()
    }
}
