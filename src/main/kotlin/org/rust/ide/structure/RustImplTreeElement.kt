package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustTypeElement

class RustImplTreeElement(element: RustImplItemElement) : PsiTreeElementBase<RustImplItemElement>(element) {

    override fun getPresentableText(): String? {
        val type: RustTypeElement = element?.type ?: return "<unknown>"

        val traitRef = element?.traitRef ?: return type.text

        return "${traitRef.text} for ${type.text}"
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val impl = element ?: return emptyList()
        return listOf(
            impl.functionList.map(::RustFunctionTreeElement),
            impl.constantList.map(::RustConstantTreeElement)
        ).flatten().sortedBy { it.element?.textOffset }
    }
}
