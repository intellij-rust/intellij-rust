package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustEnumVariantElement

class RustEnumVariantTreeElement(element: RustEnumVariantElement) : PsiTreeElementBase<RustEnumVariantElement>(element) {

    override fun getPresentableText(): String? {
        var text = element?.name

        val params = element?.tupleFields?.tupleFieldDeclList?.map { it.type.text }?.joinToString()
        if (params != null)
            text += "($params)"

        return text
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
