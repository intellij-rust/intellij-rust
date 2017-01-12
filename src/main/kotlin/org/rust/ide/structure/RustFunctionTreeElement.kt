package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.impl.mixin.valueParameters

class RustFunctionTreeElement(element: RsFunction) : PsiTreeElementBase<RsFunction>(element) {

    override fun getPresentableText(): String? {
        var text = element?.name

        val params = element?.valueParameters?.map { it.type?.text }?.joinToString()
        if (params != null)
            text += "($params)"

        val retType = element?.retType
        if (retType != null)
            text += " ${retType.text}"

        return text
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
