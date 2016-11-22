package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.namedFields

class RustStructTreeElement(element: RustStructItemElement) : PsiTreeElementBase<RustStructItemElement>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = (element?.namedFields ?: emptyList()).map(::RustStructDeclFieldTreeElement)

}
