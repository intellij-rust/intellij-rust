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
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.TextWithIcon
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.icons.RsIcons
import org.rust.ide.utils.import.ImportCandidate
import org.rust.openapiext.isUnitTestMode
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.ListCellRenderer

private var MOCK: ImportItemUi? = null

fun showItemsToImportChooser(
    project: Project,
    dataContext: DataContext,
    items: List<ImportCandidate>,
    callback: (ImportCandidate) -> Unit
) {
    val itemImportUi = if (isUnitTestMode) {
        MOCK ?: error("Multiple items: ${items.map { it.info.usePath }}. You should set mock ui via `withMockImportItemUi`")
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
        val candidatePsiItems = items.map(::ImportCandidatePsiElement)

        // TODO: sort items in popup
        val step = object : BaseListPopupStep<ImportCandidatePsiElement>("Item to Import", candidatePsiItems) {
            override fun isAutoSelectionEnabled(): Boolean = false
            override fun isSpeedSearchEnabled(): Boolean = true
            override fun hasSubstep(selectedValue: ImportCandidatePsiElement?): Boolean = false

            override fun onChosen(selectedValue: ImportCandidatePsiElement?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == null) return PopupStep.FINAL_CHOICE
                return doFinalStep { callback(selectedValue.importCandidate) }
            }

            override fun getTextFor(value: ImportCandidatePsiElement): String = value.importCandidate.info.usePath
            override fun getIconFor(value: ImportCandidatePsiElement): Icon? = value.importCandidate.item.getIcon(0)
        }
        val popup = object : ListPopupImpl(project, step) {
            override fun getListElementRenderer(): ListCellRenderer<*> {
                @Suppress("UNCHECKED_CAST")
                val baseRenderer = super.getListElementRenderer() as PopupListElementRenderer<Any>
                val psiRenderer = RsImportCandidateCellRenderer()
                return ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
                    @Suppress("MissingAccessibleContext")
                    val panel = JPanel(BorderLayout())
                    baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    panel.add(baseRenderer.nextStepLabel, BorderLayout.EAST)
                    panel.add(psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus))
                    panel
                }
            }
        }
        NavigationUtil.hidePopupIfDumbModeStarts(popup, project)
        popup.showInBestPositionFor(dataContext)
    }
}

private class ImportCandidatePsiElement(val importCandidate: ImportCandidate) : FakePsiElement() {
    override fun getParent(): PsiElement? = importCandidate.item.parent
}

private class RsImportCandidateCellRenderer : DefaultPsiElementCellRenderer() {

    private val Any.importCandidate: ImportCandidate? get() = (this as? ImportCandidatePsiElement)?.importCandidate

    override fun getIcon(element: PsiElement): Icon =
        element.importCandidate?.item?.getIcon(iconFlags) ?: super.getIcon(element)

    override fun getElementText(element: PsiElement): String =
        element.importCandidate?.itemName ?: super.getElementText(element)

    override fun getContainerText(element: PsiElement, name: String): String? {
        val importCandidate = element.importCandidate
        return if (importCandidate != null) {
            val path = importCandidate.path
            val container = if (path.size == 1) {
                path.single()
            } else {
                path.dropLast(1)
                    .joinToString("::")
                    .removePrefix("crate::")
            }
            "($container)"
        } else {
            super.getContainerText(element, name)
        }
    }

    override fun getItemLocation(value: Any?): TextWithIcon? {
        val crate = value?.importCandidate?.crate ?: return null
        return when (crate.origin) {
            PackageOrigin.STDLIB, PackageOrigin.STDLIB_DEPENDENCY -> TextWithIcon(crate.normName, RsIcons.RUST)
            PackageOrigin.DEPENDENCY -> TextWithIcon(crate.normName, CargoIcons.ICON)
            else -> null
        }
    }
}
