package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustEnumVariant

class RustEnumVariantTreeElement(element: RustEnumVariant) : PsiTreeElementBase<RustEnumVariant>(element) {

    override fun getPresentableText() = element?.identifier?.text

    override fun getChildrenBase() = arrayListOf<StructureViewTreeElement>()
}
