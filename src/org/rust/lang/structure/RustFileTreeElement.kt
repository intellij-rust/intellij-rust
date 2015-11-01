package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFileImpl

class RustFileTreeElement(file: RustFileImpl) : PsiTreeElementBase<RustFileImpl>(file) {
    override fun getPresentableText() = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = arrayListOf<StructureViewTreeElement>()
        element?.accept(object : RustVisitor() {
            override fun visitElement(o: PsiElement) {
                o.acceptChildren(this)
            }

            override fun visitEnumItem(o: RustEnumItem) {
                result += (RustEnumTreeElement(o))
            }

            override fun visitTraitItem(o: RustTraitItem) {
                result += (RustTraitTreeElement(o))
            }

            override fun visitStructItem(o: RustStructItem) {
                result += (RustStructTreeElement(o))
            }

            override fun visitImplItem(o: RustImplItem) {
                result += (RustImplTreeElement(o))
            }

            override fun visitFnItem(o: RustFnItem) {
                result += (RustFnTreeElement(o))
            }
        })
        return result;
    }
}
