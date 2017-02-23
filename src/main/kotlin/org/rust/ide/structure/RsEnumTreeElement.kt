package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsEnumItem

class RsEnumTreeElement(element: RsEnumItem) : PsiTreeElementBase<RsEnumItem>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = element?.enumBody?.enumVariantList.orEmpty().map(::RsBaseTreeElement)

}
