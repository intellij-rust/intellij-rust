package org.rust.ide.inspections.duplicates

import com.intellij.psi.PsiElement
import org.rust.ide.inspections.RustLocalInspectionTool
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustNamedElement

abstract class RustDuplicateInspectionTool : RustLocalInspectionTool() {
    protected inline fun <reified T : Any, E: RustNamedElement> createInspection(
        crossinline memberList: T.() -> List<E>,
        crossinline holderFunc: (E) -> Unit
    ) = object : RustElementVisitor() {
        override fun visitElement(element: PsiElement) {
            (element as? T)?.memberList()?.findDuplicates()?.forEach {
                holderFunc(it)
            }
        }
    }
}
