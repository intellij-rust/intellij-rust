package org.rust.lang.core.psi.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustVisitor

abstract class RustAncestorVisitor : RustVisitor() {
    override fun visitElement(element: PsiElement?) {
        visitParent(element)
    }

    final protected fun visitParent(element: PsiElement?) {
        element?.parent?.accept(this)
    }
}


