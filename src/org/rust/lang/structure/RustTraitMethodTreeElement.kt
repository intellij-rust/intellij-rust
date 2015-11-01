package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustTraitMethod

class RustTraitMethodTreeElement(element: RustTraitMethod) : PsiTreeElementBase<RustTraitMethod>(element) {

    override fun getPresentableText(): String? {
        val method = element?.method
        val typeMethod = element?.typeMethod

        val identifier = method?.identifier ?: typeMethod?.identifier
        var text = identifier?.text ?: return "<unknown>"

        val params = (method?.anonParams ?: typeMethod?.anonParams)?.anonParamList?.map { it.text }?.joinToString()
        if (params != null)
            text += "($params)"

        val retType = method?.retType ?: typeMethod?.retType;
        if (retType != null)
            text += " ${retType.text}"

        return text;
    }

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
