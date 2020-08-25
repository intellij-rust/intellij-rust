/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import org.jdom.Element
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.readPath
import org.rust.cargo.runconfig.readString
import org.rust.cargo.runconfig.ui.WasmPackCommandConfigurationEditor
import org.rust.cargo.runconfig.writePath
import org.rust.cargo.runconfig.writeString
import org.rust.cargo.toolchain.WasmPackCommandLine
import java.nio.file.Path

class WasmPackCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {

    var command: String = "build"
    var workingDirectory: Path? = project.cargoProjects.allProjects.firstOrNull()?.workingDirectory

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        WasmPackCommandConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val wasmPack = environment.project.toolchain?.wasmPack() ?: return null
        val workingDirectory = workingDirectory?.toFile() ?: return null

        return WasmPackCommandRunState(environment, this, wasmPack, workingDirectory)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("command", command)
        element.writePath("workingDirectory", workingDirectory)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readString("command")?.let { command = it }
        element.readPath("workingDirectory")?.let { workingDirectory = it }
    }

    override fun suggestedName(): String? {
        return command.substringBefore(' ').capitalize()
    }

    fun setFromCmd(cmd: WasmPackCommandLine) {
        command = ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())
        workingDirectory = cmd.workingDirectory
    }
}
