package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.util.RecursiveRustVisitor

val RustModItem.items: List<RustItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustItem::class.java)

val RustPat.boundElements: List<RustNamedElement>
    get() {
        val result = arrayListOf<RustNamedElement>()

        accept(object : RecursiveRustVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is RustNamedElement)
                    result.add(element)
                super.visitElement(element)
            }
        })
        return result
    }
