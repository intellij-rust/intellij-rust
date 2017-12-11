/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.UIUtil
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.openapiext.Testmark
import org.rust.openapiext.runWriteCommandAction
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class ImportNameIntention : RsElementBaseIntentionAction<ImportNameIntention.Context>(), HighPriorityAction {

    override fun getText() = "Import"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.ancestorStrict<RsPath>() ?: return null
        if (TyPrimitive.fromPath(path) != null) return null
        val basePath = getBasePath(path)
        if (basePath.reference.resolve() != null) return null
        val pathMod = path.containingMod
        val pathSuperMods = HashSet(pathMod.superMods)
        // TODO: support reexports
        val candidates = RsNamedElementIndex.findElementsByName(project, basePath.referenceName)
            .filterIsInstance<RsQualifiedNamedElement>()
            .filter { (it as? RsVisible)?.isVisibleIn(pathSuperMods) ?: false }
        if (candidates.isEmpty()) return null
        return Context(path, candidates)
    }

    // Semantic signature of method is `RsVisible.isVisibleIn(mod: RsMod)`
    // but in our case `mod` is always same and `mod` needs only to get set of its super mods
    // so we pass `superMods` instead of `mod` for optimization
    private fun RsVisible.isVisibleIn(superMods: Set<RsMod>): Boolean {
        val parentMod = (if (this is RsMod) `super` else containingMod) ?: return false
        val ourSuperMods = parentMod.superMods

        // try to find latest common ancestor module of `parentMod` and `mod` in module tree
        // we need to do it because we can use direct child items of any super mod with any visibility
        val lca = ourSuperMods.find { it in superMods }

        val shouldBePublicMods = if (lca == null) {
            if (!isPublic) return false
            ourSuperMods
        } else {
            // if current item is direct child of some ancestor of `mod` then it can be not public
            if (parentMod == lca) return true
            if (!isPublic) return false
            ourSuperMods.takeWhile { it != lca }.dropLast(1)
        }
        return shouldBePublicMods.all { it.isPublic }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (path, candidates) = ctx

        if (candidates.size == 1) {
            importItem(project, candidates.first(), path)
        } else {
            chooseItemAndImport(project, editor, candidates, path)
        }
    }

    private fun chooseItemAndImport(project: Project, editor: Editor, items: List<RsQualifiedNamedElement>, originalPath: RsPath) {
        // TODO: sort items in popup
        val step = object : BaseListPopupStep<RsQualifiedNamedElement>("Item to Import", items) {
            override fun isAutoSelectionEnabled(): Boolean = false
            override fun isSpeedSearchEnabled(): Boolean = true
            override fun hasSubstep(selectedValue: RsQualifiedNamedElement?): Boolean = false

            override fun onChosen(selectedValue: RsQualifiedNamedElement?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == null) return PopupStep.FINAL_CHOICE
                return doFinalStep { importItem(project, selectedValue, originalPath) }
            }

            override fun getTextFor(value: RsQualifiedNamedElement): String = value.qualifiedName!!

            override fun getIconFor(value: RsQualifiedNamedElement): Icon? = value.getIcon(0)
        }
        val popup = object : ListPopupImpl(step) {
            override fun getListElementRenderer(): ListCellRenderer<*> {
                val baseRenderer = super.getListElementRenderer() as PopupListElementRenderer<Any>
                val psiRenderer = RsElementCellRenderer()
                return ListCellRenderer<Any> { list, value, index, isSelected, cellHasFocus ->
                    val panel = JPanel(BorderLayout())
                    baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    panel.add(baseRenderer.nextStepLabel, BorderLayout.EAST)
                    panel.add(psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus))
                    panel
                }
            }
        }
        NavigationUtil.hidePopupIfDumbModeStarts(popup, project)
        popup.showInBestPositionFor(editor)
    }

    private fun importItem(project: Project, item: RsQualifiedNamedElement, originalPath: RsPath) = project.runWriteCommandAction {
        val mod = originalPath.containingMod

        val pathCrate = originalPath.crateRoot
        val elementCrate = item.crateRoot

        val externCrateItems = mod.childrenOfType<RsExternCrateItem>()
        var lastExternCreateItem = externCrateItems.maxBy { it.textOffset }

        val psiFactory = RsPsiFactory(project)

        val usePath = if (pathCrate == elementCrate) {
            item.crateRelativePath?.removePrefix("::")
        } else {
            // if crate of importing element differs from current crate
            // we need to add new extern crate item
            val createName = elementCrate?.containingCargoTarget?.normName ?: return@runWriteCommandAction
            if (createName == AutoInjectedCrates.std) {
                // but if crate of imported element is `std`
                // we don't add corresponding extern crate item manually
                // because it will be done by compiler implicitly
                Testmarks.autoInjectedCrate.hit()
            } else {
                val needAddExternCrateItem = externCrateItems.none { it.identifier.text == createName }
                if (needAddExternCrateItem) {
                    val externCrateItem = psiFactory.createExternCrateItem(createName)
                    lastExternCreateItem = if (lastExternCreateItem != null) {
                        mod.addAfter(externCrateItem, lastExternCreateItem)
                    } else {
                        val insertedItem = mod.addBefore(externCrateItem, mod.firstItem())
                        mod.addAfter(psiFactory.createNewline(), mod.firstItem())
                        insertedItem
                    } as RsExternCrateItem?
                }
            }

            item.qualifiedName
        } ?: return@runWriteCommandAction

        val lastUseItem = mod.childrenOfType<RsUseItem>().maxBy { it.textOffset }
        val useItem = psiFactory.createUseItem(usePath)
        val anchor = lastUseItem ?: lastExternCreateItem

        if (anchor != null) {
            val insertedUseItem = mod.addAfter(useItem, anchor)
            if (anchor == lastExternCreateItem) {
                mod.addBefore(psiFactory.createNewline(), insertedUseItem)
            }
        } else {
            mod.addBefore(useItem, mod.firstItem())
            mod.addAfter(psiFactory.createNewline(), mod.firstItem())
        }
    }

    data class Context(
        val path: RsPath,
        val candidates: List<RsQualifiedNamedElement>
    )

    object Testmarks {
        val autoInjectedCrate = Testmark("autoInjectedCrate")
    }
}

private fun RsItemsOwner.firstItem(): RsElement = itemsAndMacros.first()

private tailrec fun getBasePath(path: RsPath): RsPath {
    val qualifier = path.path
    return if (qualifier == null) path else getBasePath(qualifier)
}

private class RsElementCellRenderer : DefaultPsiElementCellRenderer() {

    private val rightRender: LibraryCellRender = LibraryCellRender()

    override fun getRightCellRenderer(value: Any?): DefaultListCellRenderer? = rightRender
}

private class LibraryCellRender : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int,
                                              isSelected: Boolean, cellHasFocus: Boolean): Component {
        val component = super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus)
        val textWithIcon = textWithIcon(value)
        if (textWithIcon != null) {
            text = textWithIcon.first
            icon = textWithIcon.second
        }

        border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
        horizontalTextPosition = SwingConstants.LEFT
        background = if (isSelected) UIUtil.getListSelectionBackground() else UIUtil.getListBackground()
        foreground = if (isSelected) UIUtil.getListSelectionForeground() else UIUtil.getInactiveTextColor()
        return component
    }

    private fun textWithIcon(value: Any?): Pair<String, Icon>? {
        val pkg= (value as? RsElement)?.containingCargoPackage ?: return null
        return when (pkg.origin) {
            PackageOrigin.STDLIB -> pkg.normName to RsIcons.RUST
            PackageOrigin.DEPENDENCY, PackageOrigin.TRANSITIVE_DEPENDENCY -> pkg.normName to CargoIcons.ICON
            else -> null
        }
    }
}
