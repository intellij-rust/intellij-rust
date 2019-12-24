/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractFunction

import com.intellij.openapi.project.Project
import com.intellij.refactoring.util.AbstractParameterTablePanel
import com.intellij.refactoring.util.AbstractVariableData
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo

class ParameterDataHolder(val parameter: Parameter, val onChange: () -> Unit) : AbstractVariableData() {
    fun changeName(name: String) {
        parameter.name = name
        onChange()
    }
}

class ChooseColumn : ColumnInfo<ParameterDataHolder, Boolean>(null) {
    override fun valueOf(item: ParameterDataHolder): Boolean =
        item.parameter.isSelected

    override fun setValue(item: ParameterDataHolder, value: Boolean) {
        item.parameter.isSelected = value
    }

    override fun getColumnClass(): Class<*> = Boolean::class.java

    override fun isCellEditable(item: ParameterDataHolder): Boolean = true
}

class NameColumn(private val nameValidator: (String) -> Boolean) : ColumnInfo<ParameterDataHolder, String>("Name") {
    override fun valueOf(item: ParameterDataHolder): String =
        item.parameter.name

    override fun setValue(item: ParameterDataHolder, value: String) {
        if (nameValidator(value)) {
            item.changeName(value)
        }
    }

    override fun isCellEditable(item: ParameterDataHolder): Boolean = true
}

class TypeColumn(val project: Project) : ColumnInfo<ParameterDataHolder, String>("Type") {
    override fun valueOf(item: ParameterDataHolder): String =
        item.parameter.type?.toString() ?: "_"
}

class ExtractFunctionParameterTablePanel(
    project: Project,
    nameValidator: (String) -> Boolean,
    private val config: RsExtractFunctionConfig,
    private val onChange: () -> Unit
) : AbstractParameterTablePanel<ParameterDataHolder>(
    ChooseColumn(),
    NameColumn(nameValidator),
    TypeColumn(project)
) {
    init {
        myTable.setDefaultRenderer(Boolean::class.java, BooleanTableCellRenderer())
        myTable.setDefaultEditor(Boolean::class.java, BooleanTableCellEditor())
        myTable.columnModel.getColumn(0).preferredWidth = WIDTH
        myTable.columnModel.getColumn(0).maxWidth = WIDTH

        init(
            config.parameters.map {
                ParameterDataHolder(it, ::updateSignature)
            }.toTypedArray()
        )
    }

    override fun doEnterAction() {}

    override fun doCancelAction() {}

    override fun updateSignature() {
        config.parameters = variableData.map { it.parameter }
        onChange()
    }

    companion object {
        private const val WIDTH = 40
    }
}
