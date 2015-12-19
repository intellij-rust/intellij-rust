package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustImplItem
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.RustTypeSum
import org.rust.lang.core.psi.visitors.RecursiveRustVisitor

class RustImplTreeElement(element: RustImplItem) : PsiTreeElementBase<RustImplItem>(element) {

    override fun getPresentableText(): String? {
        val typeSum: RustTypeSum = element?.typeSumList?.firstOrNull() ?: return "<unknown>"

        val traitRef = element?.traitRef ?: return typeSum.text;

        return "${traitRef.text} for ${typeSum.text}"
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = arrayListOf<StructureViewTreeElement>()
        element?.accept(object : RecursiveRustVisitor() {
            override fun visitImplMethod(o: RustImplMethod) {
                result += RustImplMethodTreeElement(o)
            }
        })
        return result;
    }
}
