package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsConstant

class RustConstantTreeElement(element: RsConstant) : PsiTreeElementBase<RsConstant>(element) {

    override fun getPresentableText(): String? {
        var text = element?.name
        if (element?.type != null) {
            text += ": " + element?.type?.text
        }
        return text
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()
}
