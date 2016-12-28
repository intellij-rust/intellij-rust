package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustMod

open class RustModTreeElement(item: RustMod) : PsiTreeElementBase<RustMod>(item) {
    override fun getPresentableText() = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val mod = element ?: return emptyList()
        return listOf(
            mod.enumItemList.map(::RustEnumTreeElement),
            mod.functionList.map(::RustFnTreeElement),
            mod.implItemList.map(::RustImplTreeElement),
            mod.modDeclItemList.map(::RustModDeclTreeElement),
            mod.modItemList.map(::RustModTreeElement),
            mod.staticItemList.map(::RustStaticTreeElement),
            mod.structItemList.map(::RustStructTreeElement),
            mod.traitItemList.map(::RustTraitTreeElement),
            mod.typeItemList.map(::RustTypeTreeElement)
        ).flatten().sortedBy { it.element?.textOffset }
    }
}
