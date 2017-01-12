package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsType

class RustImplTreeElement(element: RsImplItem) : PsiTreeElementBase<RsImplItem>(element) {

    override fun getPresentableText(): String? {
        val type: RsType = element?.type ?: return "<unknown>"

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
