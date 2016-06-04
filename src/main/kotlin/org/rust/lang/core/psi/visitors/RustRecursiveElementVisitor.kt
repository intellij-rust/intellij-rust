package org.rust.lang.core.psi.visitors

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementVisitor

abstract class RustRecursiveElementVisitor : RustElementVisitor() {

    override fun visitElement(element: PsiElement?) {
        super.visitElement(element)
        element?.acceptChildren(this)
    }

}
