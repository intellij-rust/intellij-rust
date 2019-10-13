/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import org.rust.ide.presentation.getPresentationForStructure
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.buildList

class RsStructureViewModel(editor: Editor?, file: RsFile)
    : StructureViewModelBase(file, editor, RsStructureViewElement(file)),
      StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(
            RsNamedElement::class.java,
            RsImplItem::class.java
        )
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = element.value is RsFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        when (element.value) {
            is RsNamedFieldDecl,
            is RsModDeclItem,
            is RsConstant,
            is RsTypeAlias -> true
            else -> false
        }
}

private class RsStructureViewElement(
    val psi: RsElement
) : StructureViewTreeElement, Navigatable by (psi as NavigatablePsiElement) {

    override fun getValue(): RsElement = psi

    override fun getPresentation(): ItemPresentation = getPresentationForStructure(psi)

    override fun getChildren(): Array<TreeElement> =
        childElements.map(::RsStructureViewElement).toTypedArray()

    private val childElements: List<RsElement>
        get() {
            return when (psi) {
                is RsEnumItem -> psi.variants
                is RsTraitOrImpl -> psi.expandedMembers
                is RsMod -> extractItems(psi)
                is RsStructItem -> psi.blockFields?.namedFieldDeclList.orEmpty()
                is RsEnumVariant -> psi.blockFields?.namedFieldDeclList.orEmpty()
                is RsFunction -> psi.block?.let { extractItems(it) }.orEmpty()
                else -> emptyList()
            }
        }

    private fun extractItems(psi: RsItemsOwner): List<RsElement> =
        extractItems(psi.itemsAndMacros)

    private fun extractItems(itemsAndMacros: Sequence<RsElement>): List<RsElement> =
        buildList {
            for (item in itemsAndMacros) {
                when (item) {
                    is RsMacro,
                    is RsFunction,
                    is RsModDeclItem,
                    is RsModItem,
                    is RsStructOrEnumItemElement,
                    is RsTraitOrImpl,
                    is RsTypeAlias,
                    is RsConstant -> add(item)

                    is RsForeignModItem -> {
                        for (child in item.children) {
                            if (child is RsFunction || child is RsConstant) {
                                add(child as RsElement)
                            }
                        }
                    }

                    is RsMacroCall -> {
                        addAll(extractItems(item.expansionFlatten.asSequence()))
                    }
                }
            }
        }
}
