/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.target.RsLanguageRuntimeConfiguration
import org.rust.cargo.runconfig.target.RsLanguageRuntimeType
import java.nio.file.Path


abstract class RsCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction,
    TargetEnvironmentAwareRunProfile {
    abstract var command: String

    var workingDirectory: Path? = project.cargoProjects.allProjects.firstOrNull()?.workingDirectory

    override fun suggestedName(): String = command.substringBefore(' ').capitalize()

    override fun canRunOn(target: TargetEnvironmentConfiguration): Boolean {
        return target.runtimes.findByType(RsLanguageRuntimeConfiguration::class.java) != null
    }

    override fun getDefaultLanguageRuntimeType(): LanguageRuntimeType<*>? {
        return LanguageRuntimeType.EXTENSION_NAME.findExtension(RsLanguageRuntimeType::class.java)
    }

    override fun getDefaultTargetName(): String? {
        return options.remoteTarget
    }

    override fun setDefaultTargetName(targetName: String?) {
        options.remoteTarget = targetName
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
