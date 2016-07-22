package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.fields

class RustStructTreeElement(element: RustStructItemElement) : PsiTreeElementBase<RustStructItemElement>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = (element?.fields ?: emptyList()).map { RustStructDeclFieldTreeElement(it) }

}
