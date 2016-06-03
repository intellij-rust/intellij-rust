package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustTraitMethodMemberElement

class RustTraitMethodTreeElement(element: RustTraitMethodMemberElement) : PsiTreeElementBase<RustTraitMethodMemberElement>(element) {

    override fun getPresentableText(): String {
        var text = element?.identifier?.text ?: return "<unknown>"

        element?.parameters?.parameterList?.let { params ->
            text += "(${params.map { it.text }.joinToString()})"
        }

        element?.retType?.let { retType ->
            text += " ${retType.text}"
        }

        return text
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
