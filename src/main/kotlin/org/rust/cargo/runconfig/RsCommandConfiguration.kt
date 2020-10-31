/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.InputRedirectAware
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.command.workingDirectory
import java.nio.file.Path


abstract class RsCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    InputRedirectAware.InputRedirectOptions {
    abstract var command: String

    var workingDirectory: Path? = project.cargoProjects.allProjects.firstOrNull()?.workingDirectory

    override fun suggestedName(): String = command.substringBefore(' ').capitalize()

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
