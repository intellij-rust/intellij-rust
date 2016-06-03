package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.psi.visitors.RecursiveRustVisitor

class RustImplTreeElement(element: RustImplItemElement) : PsiTreeElementBase<RustImplItemElement>(element) {

    override fun getPresentableText(): String? {
        val type: RustTypeElement = element?.type ?: return "<unknown>"

        val traitRef = element?.traitRef ?: return type.text;

        return "${traitRef.text} for ${type.text}"
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = arrayListOf<StructureViewTreeElement>()
        element?.accept(object : RecursiveRustVisitor() {
            override fun visitImplMethodMember(o: RustImplMethodMemberElement) {
                result += RustImplMethodTreeElement(o)
            }
        })
        return result
    }
}
