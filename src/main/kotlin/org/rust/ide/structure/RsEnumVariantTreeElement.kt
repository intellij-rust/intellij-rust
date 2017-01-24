package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsEnumVariant

class RsEnumVariantTreeElement(element: RsEnumVariant) : PsiTreeElementBase<RsEnumVariant>(element) {

    override fun getPresentableText(): String? {
        var text = element?.name

        val params = element?.tupleFields?.tupleFieldDeclList?.map { it.typeReference.text }?.joinToString()
        if (params != null)
            text += "($params)"

        return text
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
