/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.icons.RsIcons
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

private var MOCK: ImportItemUi? = null

fun showItemsToImportChooser(
    project: Project,
    dataContext: DataContext,
    items: List<ImportCandidate>,
    callback: (ImportCandidate) -> Unit
) {
    val itemImportUi = if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockImportItemUi`")
    } else {
        PopupImportItemUi(project, dataContext)
    }
    itemImportUi.chooseItem(items, callback)
}

@TestOnly
fun withMockImportItemUi(mockUi: ImportItemUi, action: () -> Unit) {
    MOCK = mockUi
    try {
        action()
    } finally {
        MOCK = null
    }
}

interface ImportItemUi {
    fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit)
}

private class PopupImportItemUi(private val project: Project, private val dataContext: DataContext) : ImportItemUi {

    override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
        // TODO: sort items in popup
        val step = object : BaseListPopupStep<ImportCandidate>("Item to Import", items) {
            override fun isAutoSelectionEnabled(): Boolean = false
            override fun isSpeedSearchEnabled(): Boolean = true
            override fun hasSubstep(selectedValue: ImportCandidate?): Boolean = false

            override fun onChosen(selectedValue: ImportCandidate?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == null) return PopupStep.FINAL_CHOICE
                return doFinalStep { callback(selectedValue) }
            }

            override fun getTextFor(value: ImportCandidate): String = value.info.usePath
            override fun getIconFor(value: ImportCandidate): Icon? = value.qualifiedNamedItem.item.getIcon(0)
        }
        val popup = object : ListPopupImpl(project, step) {
            override fun getListElementRenderer(): ListCellRenderer<*> {
                val baseRenderer = super.getListElementRenderer() as PopupListElementRenderer<Any>
                val psiRenderer = RsElementCellRenderer()
                return ListCellRenderer<Any> { list, value, index, isSelected, cellHasFocus ->
                    val panel = JPanel(BorderLayout())
                    baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    panel.add(baseRenderer.nextStepLabel, BorderLayout.EAST)
                    val importCandidate = value as? ImportCandidate
                    panel.add(psiRenderer.getListCellRendererComponent(list, importCandidate, index, isSelected, cellHasFocus))
                    panel
                }
            }
        }
        NavigationUtil.hidePopupIfDumbModeStarts(popup, project)
        popup.showInBestPositionFor(dataContext)
    }
}

private class RsElementCellRenderer : DefaultPsiElementCellRenderer() {

    private val rightRender: LibraryCellRender = LibraryCellRender()

    private var importCandidate: ImportCandidate? = null

    override fun getRightCellRenderer(value: Any?): DefaultListCellRenderer? = rightRender

    override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        val realValue = if (value is ImportCandidate) {
            // Generally, it's rather hacky but I don't know another way
            // how to use functionality of `PsiElementListCellRenderer`
            // and pass additional info with psi element at same time
            importCandidate = value
            value.qualifiedNamedItem.item
        } else {
            value
        }
        return super.getListCellRendererComponent(list, realValue, index, isSelected, cellHasFocus)
    }

    override fun getElementText(element: PsiElement): String = importCandidate?.qualifiedNamedItem?.itemName
        ?: super.getElementText(element)

    override fun getContainerText(element: PsiElement, name: String): String? {
        val importCandidate = importCandidate
        return if (importCandidate != null) {
            val crateName = (importCandidate.info as? ImportInfo.ExternCrateImportInfo)?.externCrateName
            val parentPath = importCandidate.qualifiedNamedItem.parentCrateRelativePath ?: return null
            val container = when {
                crateName == null -> parentPath
                parentPath.isEmpty() -> crateName
                else -> "$crateName::$parentPath"
            }
            "($container)"
        } else {
            super.getContainerText(element, name)
        }
    }

    private inner class LibraryCellRender : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int,
                                                  isSelected: Boolean, cellHasFocus: Boolean): Component {
            val component = super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus)
            val textWithIcon = textWithIcon()
            if (textWithIcon != null) {
                text = textWithIcon.first
                icon = textWithIcon.second
            }

            border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
            horizontalTextPosition = SwingConstants.LEFT
            background = UIUtil.getListBackground(isSelected, cellHasFocus)
            foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
            return component
        }

        private fun textWithIcon(): Pair<String, Icon>? {
            val pkg = importCandidate?.qualifiedNamedItem?.containingCargoTarget?.pkg ?: return null
            return when (pkg.origin) {
                PackageOrigin.STDLIB -> pkg.normName to RsIcons.RUST
                PackageOrigin.DEPENDENCY, PackageOrigin.TRANSITIVE_DEPENDENCY -> pkg.normName to CargoIcons.ICON
                else -> null
            }
        }
    }
}
