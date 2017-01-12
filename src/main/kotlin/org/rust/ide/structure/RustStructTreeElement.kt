package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.namedFields

class RustStructTreeElement(element: RsStructItem) : PsiTreeElementBase<RsStructItem>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = (element?.namedFields ?: emptyList()).map(::RustStructDeclFieldTreeElement)

}
