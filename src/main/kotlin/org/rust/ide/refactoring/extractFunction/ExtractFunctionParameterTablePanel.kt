/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractFunction

import com.intellij.openapi.project.Project
import com.intellij.refactoring.util.AbstractParameterTablePanel
import com.intellij.refactoring.util.AbstractVariableData
import com.intellij.util.ui.ColumnInfo

class ParameterDataHolder(val parameter: Parameter, val onChange: () -> Unit) : AbstractVariableData() {
    fun changeName(name: String) {
        parameter.name = name
        onChange()
    }
}

class NameColumn(private val nameValidator: (String) -> Boolean) : ColumnInfo<ParameterDataHolder, String>("Name") {
    override fun valueOf(item: ParameterDataHolder): String {
        return item.parameter.name
    }

    override fun setValue(item: ParameterDataHolder, value: String) {
        if (nameValidator(value)) {
            item.changeName(value)
        }
    }

    override fun isCellEditable(item: ParameterDataHolder): Boolean {
        return true
    }
}

class TypeColumn(val project: Project) : ColumnInfo<ParameterDataHolder, String>("Type") {
    override fun valueOf(item: ParameterDataHolder): String {
        return item.parameter.type?.toString() ?: "_"
    }
}

class ExtractFunctionParameterTablePanel(nameValidator: (String) -> Boolean,
                                         project: Project,
                                         private val config: RsExtractFunctionConfig,
                                         private val onChange: () -> Unit)
    : AbstractParameterTablePanel<ParameterDataHolder>(
    NameColumn(nameValidator),
    TypeColumn(project)
) {
    init {
        init(config.parameters.map {
            ParameterDataHolder(it) {
                updateSignature()
            }
        }.toTypedArray())
    }

    override fun doEnterAction() {}
    override fun doCancelAction() {}
    override fun updateSignature() {
        config.parameters = variableData.map { it.parameter }
        onChange()
    }
}
