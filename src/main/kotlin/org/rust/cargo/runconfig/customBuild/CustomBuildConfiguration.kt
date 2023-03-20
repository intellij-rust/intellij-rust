/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.customBuild

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.rust.RsBundle
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.readBool
import org.rust.cargo.runconfig.readPath
import org.rust.cargo.runconfig.writeBool
import org.rust.cargo.runconfig.writePath
import org.rust.openapiext.pathAsPath
import java.nio.file.Path

class CustomBuildConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : CargoCommandConfiguration(project, name, factory) {
    // This is a hack: we pretend to be a normal cargo run command but at the last
    // moment (in doExecute) the actual executable is changed to the build script's artifact.
    // We have to work this way because Cargo doesn't have a proper way to cal build scripts
    // from outside.
    override var command: String = "run"

    var crateRoot: Path? = null
        private set

    var isCustomOutDir: Boolean = false
    var customOutDir: Path? = null

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CustomBuildConfigurationEditor(project)

    fun canBeFrom(target: CargoWorkspace.Target): Boolean =
        target.crateRoot?.pathAsPath == this.crateRoot

    fun setTarget(target: CargoWorkspace.Target) {
        crateRoot = target.crateRoot?.pathAsPath
        name = RsBundle.message("run.config.rust.custom.build.target.name", target.pkg.name)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeBool("isCustomOutDir", isCustomOutDir)
        element.writePath("customOutDir", customOutDir)
        element.writePath("crateRoot", crateRoot)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readBool("isCustomOutDir")?.let { isCustomOutDir = it }
        element.readPath("customOutDir")?.let { customOutDir = it }
        element.readPath("crateRoot")?.let { crateRoot = it }
    }
}
