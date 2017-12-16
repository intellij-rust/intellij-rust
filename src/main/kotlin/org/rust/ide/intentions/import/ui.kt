/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.openapiext.isUnitTestMode
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

private var MOCK: ImportItemUi? = null

fun showItemsToImportChooser(
    project: Project,
    editor: Editor,
    items: List<ImportCandidate>,
    callback: (ImportCandidate) -> Unit
) {
    val itemImportUi = if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockImportItemUi`")
    } else {
        PopupImportItemUi(project, editor)
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

private class PopupImportItemUi(private val project: Project, private val editor: Editor) : ImportItemUi {

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
            override fun getIconFor(value: ImportCandidate): Icon? = value.item.getIcon(0)
        }
        val popup = object : ListPopupImpl(step) {
            override fun getListElementRenderer(): ListCellRenderer<*> {
                val baseRenderer = super.getListElementRenderer() as PopupListElementRenderer<Any>
                val psiRenderer = RsElementCellRenderer()
                return ListCellRenderer<Any> { list, value, index, isSelected, cellHasFocus ->
                    val panel = JPanel(BorderLayout())
                    baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    panel.add(baseRenderer.nextStepLabel, BorderLayout.EAST)
                    val item = (value as? ImportCandidate)?.item
                    panel.add(psiRenderer.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus))
                    panel
                }
            }
        }
        NavigationUtil.hidePopupIfDumbModeStarts(popup, project)
        popup.showInBestPositionFor(editor)
    }
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
        val pkg = (value as? RsElement)?.containingCargoPackage ?: return null
        return when (pkg.origin) {
            PackageOrigin.STDLIB -> pkg.normName to RsIcons.RUST
            PackageOrigin.DEPENDENCY, PackageOrigin.TRANSITIVE_DEPENDENCY -> pkg.normName to CargoIcons.ICON
            else -> null
        }
    }
}
