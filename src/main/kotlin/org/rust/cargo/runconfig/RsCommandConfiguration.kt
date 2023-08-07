/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.isUnitTestMode
import org.rust.stdext.capitalized
import java.nio.file.Path

val RsCommandConfiguration.hasRemoteTarget: Boolean
    get() = if (this is CargoCommandConfiguration) defaultTargetName != null else false

abstract class RsCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {
    abstract var command: String

    var emulateTerminal: Boolean = emulateTerminalDefault

    var workingDirectory: Path? = if (!project.isDefault) {
        project.cargoProjects.allProjects.firstOrNull()?.workingDirectory
    } else {
        null
    }

    override fun suggestedName(): String = command.substringBefore(' ').capitalized()

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("command", command)
        element.writePath("workingDirectory", workingDirectory)
        element.writeBool("emulateTerminal", emulateTerminal)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readString("command")?.let { command = it }
        element.readPath("workingDirectory")?.let { workingDirectory = it }
        element.readBool("emulateTerminal")?.let { emulateTerminal = it }
    }

    companion object {
        val emulateTerminalDefault: Boolean
            get() = isFeatureEnabled(RsExperiments.EMULATE_TERMINAL) && !isUnitTestMode
    }
}
