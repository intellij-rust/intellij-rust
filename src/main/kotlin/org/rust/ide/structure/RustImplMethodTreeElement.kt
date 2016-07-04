package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustImplMethodMemberElement

class RustImplMethodTreeElement(element: RustImplMethodMemberElement) : PsiTreeElementBase<RustImplMethodMemberElement>(element) {

    override fun getPresentableText(): String? {
        var text = element?.name

        val params = element?.parameters?.parameterList?.map { it.type?.text }?.joinToString()
        if (params != null)
            text += "($params)"

        val retType = element?.retType
        if (retType != null)
            text += " ${retType.text}"

        return text
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
