package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.RustTraitItem
import org.rust.lang.core.psi.util.RecursiveRustVisitor

class RustTraitTreeElement(element: RustTraitItem) : PsiTreeElementBase<RustTraitItem>(element) {

    override fun getPresentableText(): String? {
        var text = element?.identifier?.text ?: return "<unknown>"

        val generics = element?.genericParams?.text
        if (generics != null)
            text += generics

        val typeBounds = element?.typeParamBounds?.polyboundList?.map { it.text }?.joinToString(" + ")
        if (typeBounds != null)
            text += ": $typeBounds"

        return text
    }


    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = arrayListOf<StructureViewTreeElement>()
        result.addAll(element?.traitMethodList?.map { RustTraitMethodTreeElement(it) }.orEmpty())
        return result;
    }
}
