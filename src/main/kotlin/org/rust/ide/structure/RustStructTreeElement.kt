package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructItemElement

class RustStructTreeElement(element: RustStructItemElement) : PsiTreeElementBase<RustStructItemElement>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = getFields().orEmpty().map { RustStructDeclFieldTreeElement(it) }

    private fun getFields() = element?.structDeclArgs?.fieldDeclList
}
