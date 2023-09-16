/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.cellvalidators.ValidatingTableCellRendererWrapper
import com.intellij.openapi.ui.cellvalidators.ValidationUtils
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.rust.RsBundle
import org.rust.ide.settings.RsPathsExcludeTable.ExclusionScope
import org.rust.ide.settings.RsPathsExcludeTable.Item
import java.util.function.Supplier
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class RsPathsExcludeTable(project: Project) : ListTableWithButtons<Item>() {

    private val globalSettings: RsCodeInsightSettings = RsCodeInsightSettings.getInstance()
    private val projectSettings: RsProjectCodeInsightSettings = RsProjectCodeInsightSettings.getInstance(project)

    private fun getSettingsItems(): List<Item> =
        globalSettings.getExcludedPaths().map { Item(it.path, it.type, ExclusionScope.IDE) } +
            projectSettings.state.excludedPaths.map { Item(it.path, it.type, ExclusionScope.Project) }

    private fun getCurrentItems(): List<Item> =
        tableView.listTableModel.items

    private fun getCurrentItems(scope: ExclusionScope): Array<ExcludedPath> =
        getCurrentItems().filter { it.scope == scope }.map2Array { ExcludedPath(it.path, it.type) }

    fun isModified(): Boolean = getSettingsItems() != getCurrentItems()

    fun apply() {
        globalSettings.setExcludedPaths(getCurrentItems(ExclusionScope.IDE))
        projectSettings.state.excludedPaths = getCurrentItems(ExclusionScope.Project)
    }

    fun reset() {
        setValues(getSettingsItems())
    }

    override fun createListModel(): ListTableModel<*> = ListTableModel<Item>(PATH_COLUMN, TYPE_COLUMN, SCOPE_COLUMN)

    override fun createElement(): Item = Item("", ExclusionType.ItemsAndMethods, ExclusionScope.IDE)

    override fun isEmpty(item: Item): Boolean = item.path.isEmpty()

    override fun canDeleteElement(item: Item): Boolean = true

    override fun cloneElement(item: Item): Item = item.copy()

    data class Item(var path: String, var type: ExclusionType, var scope: ExclusionScope)
    enum class ExclusionScope { Project, IDE }
}

@Suppress("DialogTitleCapitalization")
private val PATH_COLUMN: ColumnInfo<Item, String> = object : ColumnInfo<Item, String>(RsBundle.message("column.name.item.or.module")) {
    override fun valueOf(item: Item): String = item.path

    override fun isCellEditable(item: Item): Boolean = true

    override fun setValue(item: Item, value: String) {
        item.path = value
    }

    override fun getEditor(item: Item): TableCellEditor {
        val cellEditor = ExtendableTextField()
        cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
        ComponentValidator(RsCodeInsightSettings.getInstance()).withValidator(Supplier {
            val error = getValidationInfo(cellEditor.text, cellEditor)
            ValidationUtils.setExtension(cellEditor, ValidationUtils.ERROR_EXTENSION, error != null)
            error
        }).andRegisterOnDocumentListener(cellEditor).installOn(cellEditor)
        return DefaultCellEditor(cellEditor)
    }

    override fun getRenderer(item: Item?): TableCellRenderer {
        val cellEditor = JTextField()
        cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
        @Suppress("UnstableApiUsage")
        return ValidatingTableCellRendererWrapper(DefaultTableCellRenderer())
            .withCellValidator { value, _, _ -> getValidationInfo(value?.toString(), null) }
            .bindToEditorSize(cellEditor::getPreferredSize)
    }

    private fun getValidationInfo(path: String?, component: JComponent?): ValidationInfo? {
        if (path.isNullOrEmpty() || path.matches(PATH_PATTERN)) return null
        val errorText = RsBundle.message("dialog.message.illegal.path", path)
        return ValidationInfo(errorText, component)
    }
}

private val PATH_PATTERN: Regex = Regex("(\\w+::)*\\w+(::\\*)?")

private val TYPE_COLUMN: ColumnInfo<Item, ExclusionType> = object : ComboboxColumnInfo<ExclusionType>(ExclusionType.values(), RsBundle.message("column.name.apply.to")) {

    override fun ExclusionType.displayText(): String = when (this) {
        ExclusionType.ItemsAndMethods -> RsBundle.message("label.everything")
        ExclusionType.Methods -> RsBundle.message("label.methods.only")
    }

    override fun valueOf(item: Item): ExclusionType = item.type

    override fun setValue(item: Item, value: ExclusionType) {
        item.type = value
    }
}

private val SCOPE_COLUMN: ColumnInfo<Item, ExclusionScope> = object : ComboboxColumnInfo<ExclusionScope>(ExclusionScope.values(), RsBundle.message("column.name.scope")) {

    override fun valueOf(item: Item): ExclusionScope = item.scope

    override fun setValue(item: Item, value: ExclusionScope) {
        item.scope = value
    }
}

private abstract class ComboboxColumnInfo<T : Any>(
    private val values: Array<T>,
    @NlsContexts.ColumnName name: String,
) : ColumnInfo<Item, T>(name) {

    @NlsContexts.Label
    open fun T.displayText(): String {
        @NlsSafe val toString = toString()
        return toString
    }

    override fun isCellEditable(item: Item): Boolean = true

    override fun getRenderer(pair: Item?): TableCellRenderer = renderer

    override fun getEditor(pair: Item?): TableCellEditor = renderer

    private val renderer: ComboBoxTableRenderer<T> =
        object : ComboBoxTableRenderer<T>(values) {
            override fun getTextFor(value: T): String = value.displayText()
        }

    override fun getMaxStringValue(): String =
        values.map { it.displayText() }.maxByOrNull { it.length }!!

    override fun getAdditionalWidth(): Int =
        JBUIScale.scale(12) + AllIcons.General.ArrowDown.iconWidth
}
