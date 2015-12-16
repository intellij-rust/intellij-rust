package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructItem

class RustStructTreeElement(element: RustStructItem) : PsiTreeElementBase<RustStructItem>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = getDeclFields().orEmpty().map { RustStructDeclFieldTreeElement(it) }

    fun getDeclFields() = element?.structDeclArgs?.structDeclFieldList
}
