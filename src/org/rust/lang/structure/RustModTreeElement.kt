package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.*

class RustModTreeElement(item: RustModItem) : PsiTreeElementBase<RustModItem>(item) {
    override fun getPresentableText() = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        return element?.items?.map { toTreeElement(it) }?.filterNotNull().orEmpty()
    }

    private fun toTreeElement(it: RustItem): StructureViewTreeElement? {
        return when (it) {
            is RustEnumItem -> RustEnumTreeElement(it)
            is RustTraitItem -> RustTraitTreeElement(it)
            is RustStructItem -> RustStructTreeElement(it)
            is RustImplItem -> RustImplTreeElement(it)
            is RustFnItem -> RustFnTreeElement(it)
            is RustModItem -> RustModTreeElement(it)
            else -> null
        }
    }
}
