package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustImplItem
import org.rust.lang.core.psi.RustImplMethodMember
import org.rust.lang.core.psi.RustType
import org.rust.lang.core.psi.visitors.RecursiveRustVisitor

class RustImplTreeElement(element: RustImplItem) : PsiTreeElementBase<RustImplItem>(element) {

    override fun getPresentableText(): String? {
        val type: RustType = element?.type ?: return "<unknown>"

        val traitRef = element?.traitRef ?: return type.text;

        return "${traitRef.text} for ${type.text}"
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = arrayListOf<StructureViewTreeElement>()
        element?.accept(object : RecursiveRustVisitor() {
            override fun visitImplMethodMember(o: RustImplMethodMember) {
                result += RustImplMethodTreeElement(o)
            }
        })
        return result
    }
}
