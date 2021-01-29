/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.test.CargoTestConsoleProperties
import java.nio.file.Path


abstract class RsCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction,
    SMRunnerConsolePropertiesProvider {
    abstract var command: String

    var workingDirectory: Path? = project.cargoProjects.allProjects.firstOrNull()?.workingDirectory

    override fun suggestedName(): String = command.substringBefore(' ').capitalize()

    override fun createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties {
        return CargoTestConsoleProperties(this, executor)
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
}
