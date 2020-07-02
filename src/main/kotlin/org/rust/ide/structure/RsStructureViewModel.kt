/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.TreeAnchorizer
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Queryable
import com.intellij.openapiext.isUnitTestMode
import com.intellij.pom.Navigatable
import com.intellij.ui.icons.RowIcon
import com.intellij.util.PlatformIcons
import org.rust.ide.presentation.getPresentationForStructure
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.buildList

class RsStructureViewModel(editor: Editor?, file: RsFileBase)
    : StructureViewModelBase(file, editor, RsStructureViewElement(file)),
      StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(
            RsNamedElement::class.java,
            RsImplItem::class.java
        )
        withSorters(Sorter.ALPHA_SORTER)
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

class RsStructureViewElement(
    psiArg: RsElement
) : StructureViewTreeElement, Queryable {

    private val psiAnchor = TreeAnchorizer.getService().createAnchor(psiArg)
    private val psi: RsElement? get() = TreeAnchorizer.getService().retrieveElement(psiAnchor) as? RsElement

    override fun navigate(requestFocus: Boolean) {
        (psi as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (psi as? Navigatable)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = (psi as? Navigatable)?.canNavigateToSource() == true

    override fun getValue(): RsElement? = psi

    override fun getPresentation(): ItemPresentation {
        return psi?.let(::getPresentationForStructure)
            ?: PresentationData("", null, null, null)
    }

    override fun getChildren(): Array<TreeElement> =
        childElements.map(::RsStructureViewElement).toTypedArray()

    private val childElements: List<RsElement>
        get() {
            return when (val psi = psi) {
                is RsEnumItem -> psi.variants
                is RsTraitOrImpl -> psi.expandedMembers
                is RsMod -> extractItems(psi)
                is RsStructItem -> psi.blockFields?.namedFieldDeclList.orEmpty()
                is RsEnumVariant -> psi.blockFields?.namedFieldDeclList.orEmpty()
                is RsFunction -> psi.block?.let { extractItems(it) }.orEmpty()
                is RsReplCodeFragment -> psi.namedElementsUnique.values + psi.getVariablesDeclarations()
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

    // Used in `RsStructureViewTest`
    override fun putInfo(info: MutableMap<String, String>) {
        if (!isUnitTestMode) return

        val presentation = presentation
        info[NAME_KEY] = presentation.presentableText ?: ""
        val icon = (presentation.getIcon(false) as? RowIcon)?.allIcons?.getOrNull(1)

        info[VISIBILITY_KEY] = when (icon) {
            PlatformIcons.PUBLIC_ICON -> "public"
            PlatformIcons.PRIVATE_ICON -> "private"
            PlatformIcons.PROTECTED_ICON -> "restricted"
            null -> "none"
            else -> "unknown"
        }
    }

    companion object {
        const val NAME_KEY: String = "name"
        const val VISIBILITY_KEY = "visibility"
    }
}

private fun RsReplCodeFragment.getVariablesDeclarations(): Collection<RsPatBinding> {
    val variables = mutableMapOf<String, RsPatBinding>()
    for (letDecl in childrenOfType<RsLetDecl>()) {
        val pat = letDecl.pat ?: continue
        for (patBinding in pat.descendantsOfType<RsPatBinding>()) {
            val name = patBinding.name ?: continue
            variables[name] = patBinding
        }
    }
    return variables.values
}
