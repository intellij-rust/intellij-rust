package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RsMod

open class RsModTreeElement(item: RsMod) : PsiTreeElementBase<RsMod>(item) {
    override fun getPresentableText() = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val mod = element ?: return emptyList()
        return listOf(
            mod.enumItemList.map(::RsEnumTreeElement),
            mod.functionList.map(::RsFunctionTreeElement),
            mod.implItemList.map(::RsImplTreeElement),
            mod.modDeclItemList.map(::RsBaseTreeElement),
            mod.modItemList.map(::RsModTreeElement),
            mod.constantList.map(::RsBaseTreeElement),
            mod.structItemList.map(::RsStructTreeElement),
            mod.traitItemList.map(::RsTraitTreeElement),
            mod.typeAliasList.map(::RsBaseTreeElement)
        ).flatten().sortedBy { it.element?.textOffset }
    }
}
