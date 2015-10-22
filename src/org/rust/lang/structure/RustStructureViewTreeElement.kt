package org.rust.lang.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.impl.RustFnItemImpl
import org.rust.lang.core.psi.impl.RustImplMethodImpl

class RustStructureViewTreeElement(file: RustFileImpl) : PsiTreeElementBase<RustFileImpl>(file) {
    override fun getPresentableText(): String? = element?.name


    override fun getChildrenBase(): Collection<RustStructureViewElement> {
        var result = listOf<RustStructureViewElement>()
        element?.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun elementFinished(element: PsiElement?) {
                when (element) {
                    is RustFnItemImpl ->
                        result += RustStructureViewElement(element)
                    is RustImplMethodImpl ->
                        result += RustStructureViewElement(element)
                }
            }
        })

        return result;
    }
}