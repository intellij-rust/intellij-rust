package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructItem

class RustStructTreeElement(element: RustStructItem) : PsiTreeElementBase<RustStructItem>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = getFields().orEmpty().map { RustStructDeclFieldTreeElement(it) }

    private fun getFields() = element?.structDeclArgs?.fieldDeclList
}
