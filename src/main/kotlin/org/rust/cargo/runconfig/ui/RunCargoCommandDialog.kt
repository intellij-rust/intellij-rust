/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.CargoCommandLineEditor
import javax.swing.JComponent

class RunCargoCommandDialog(
    project: Project,
    workspace: CargoWorkspace?
) : DialogWrapper(project, false) {
    private val commandField = CargoCommandLineEditor(project, workspace)


    init {
        init()
        title = "Run Cargo Command"
    }

    override fun createCenterPanel(): JComponent = panel {
        val label = Label("&Command line")
        row(label) {
            commandField.apply {
                setPreferredWidth(400)
                attachLabel(label)
//                SwingUtilities.invokeLater { commandField.textField.requestFocusInWindow() }
            }()
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = commandField.preferredFocusedComponent

    fun getCargoCommandLine(): CargoCommandLine {
        val params = ParametersListUtil.parse(commandField.text)
        return CargoCommandLine(params.first(), params.drop(1))
    }

    override fun doValidate(): ValidationInfo? {
        if (commandField.text.isBlank()) {
            return ValidationInfo("Specify command", commandField)
        }
        return null
    }
}
