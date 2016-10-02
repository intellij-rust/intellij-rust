package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustTraitMethodMemberElement

class RustTraitMethodTreeElement(element: RustTraitMethodMemberElement) : PsiTreeElementBase<RustTraitMethodMemberElement>(element) {

    override fun getPresentableText(): String {
        var text = element?.identifier?.text ?: return "<unknown>"

        val params = element?.parameters?.parameterList?.map { it.type?.text }?.joinToString()
        if (params != null)
            text += "($params)"

        element?.retType?.let { retType ->
            text += " ${retType.text}"
        }

        return text
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
