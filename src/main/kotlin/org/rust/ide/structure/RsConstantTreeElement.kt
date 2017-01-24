package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsConstant

class RsConstantTreeElement(element: RsConstant) : PsiTreeElementBase<RsConstant>(element) {

    override fun getPresentableText(): String? {
        var text = element?.name
        if (element?.typeReference != null) {
            text += ": " + element?.typeReference?.text
        }
        return text
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()
}
