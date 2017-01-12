package org.rust.lang.core.psi.visitors

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsVisitor

abstract class RustRecursiveElementVisitor : RsVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
    }

}
