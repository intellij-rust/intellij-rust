package org.rust.cargo.runconfig.ui

import backcompat.ui.layout.panel
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TextFieldWithHistory
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.toolchain.CargoCommandLine
import javax.swing.JComponent

class RunCargoCommandDialog(private val project: Project) : DialogWrapper(project, false) {
    private val commandField = TextFieldWithHistory()

    init {
        init()
        commandField.history = readHistory()
        title = "Run Cargo Command"
    }

    override fun createCenterPanel(): JComponent = panel {
        row("&Command line") {
            commandField.apply {
                // BACKCOMPAT: 2017.1
                // Should be done automatically in `addGrowIfNeed`
                setMinimumAndPreferredWidth(350)
            }()
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = commandField

    fun getCargoCommandLine(): CargoCommandLine {
        commandField.addCurrentTextToHistory()
        writeHistory(commandField.history)
        val params = ParametersListUtil.parse(commandField.text)
        return CargoCommandLine(params.first(), params.drop(1))
    }

    override fun doValidate(): ValidationInfo? {
        if (commandField.text.isBlank()) {
            return ValidationInfo("Specify command", commandField)
        }
        return null
    }

    private fun readHistory(): List<String> =
        PropertiesComponent.getInstance(project)
            .getValue(KEY, "")
            .split(Companion.SEPARATOR)

    private fun writeHistory(history: List<String>) {
        PropertiesComponent.getInstance(project)
            .setValue(KEY, history.joinToString(SEPARATOR))
    }

    companion object {
        private val SEPARATOR = "%%%%"
        private val KEY = "RunCargoCommandDialog.history"
    }
}
