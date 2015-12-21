package org.rust.ide.structure

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustEnumItem

class RustEnumTreeElement(element: RustEnumItem) : PsiTreeElementBase<RustEnumItem>(element) {

    override fun getPresentableText() = element?.name

    override fun getChildrenBase() = getDefs().orEmpty().map { RustEnumDefTreeElement(it) }

    fun getDefs() = element?.enumDefList
}
