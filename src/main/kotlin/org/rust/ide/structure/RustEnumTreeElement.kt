package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsEnumItem

class RustEnumTreeElement(element: RsEnumItem) : PsiTreeElementBase<RsEnumItem>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = getVariants().orEmpty().map(::RustEnumVariantTreeElement)

    private fun getVariants() = element?.enumBody?.enumVariantList
}
