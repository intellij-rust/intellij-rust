package org.rust.lang.core.psi.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustVisitor

abstract class RecursiveRustVisitor : RustVisitor() {
    override fun visitElement(element: PsiElement?) {
        super.visitElement(element)
        element?.acceptChildren(this)
    }
}