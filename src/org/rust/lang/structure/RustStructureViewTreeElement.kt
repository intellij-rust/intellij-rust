package org.rust.lang.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFileImpl

class RustStructureViewTreeElement(file: RustFileImpl) : PsiTreeElementBase<RustFileImpl>(file) {
    override fun getPresentableText(): String? = element?.name


    override fun getChildrenBase(): Collection<RustStructureViewElement> {
        val result = arrayListOf<RustStructureViewElement>()
        element?.accept(object : RustVisitor() {
            override fun visitElement(o: PsiElement) {
                o.acceptChildren(this)
            }

            override fun visitEnumItem(o: RustEnumItem) {
                result += (RustStructureViewElement(o))
            }

            override fun visitStructItem(o: RustStructItem) {
                result += (RustStructureViewElement(o))
            }

            override fun visitFnItem(o: RustFnItem) {
                result += (RustStructureViewElement(o))
            }

            override fun visitImplMethod(o: RustImplMethod) {
                result += (RustStructureViewElement(o))
            }
        })
        return result;
    }
}
