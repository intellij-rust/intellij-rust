/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfiguration
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfigurationType
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.Cargo.Companion.GeneratedFilesHolder
import org.rust.ide.statistics.RsCounterUsagesCollector
import org.rust.openapiext.RsProcessResult
import org.rust.openapiext.isHeadlessEnvironment
import org.rust.stdext.toPath

fun Cargo.makeProject(
    project: Project,
    module: Module,
    baseDir: VirtualFile,
    name: String,
    template: RsProjectTemplate,
    vcs: String? = null
): RsProcessResult<GeneratedFilesHolder> {
    RsCounterUsagesCollector.newProjectCreation(template)
    return when (template) {
        is RsGenericTemplate -> init(project, module, baseDir, name, template.isBinary, vcs)
        is RsCustomTemplate -> generate(project, module, baseDir, name, template.url, vcs)
    }
}

fun Project.openFiles(files: GeneratedFilesHolder) = invokeLater {
    if (!isHeadlessEnvironment) {
        val navigation = PsiNavigationSupport.getInstance()
        navigation.createNavigatable(this, files.manifest, -1).navigate(false)
        for (file in files.sourceFiles) {
            navigation.createNavigatable(this, file, -1).navigate(true)
        }
    }
}

fun Project.makeDefaultRunConfiguration(template: RsProjectTemplate) {
    val runManager = RunManager.getInstance(this)
    val configurationFactory = DefaultRunConfigurationFactory(runManager, this)

    val configuration = when (template) {
        is RsGenericTemplate.CargoBinaryTemplate -> configurationFactory.createCargoRunConfiguration()
        is RsGenericTemplate.CargoLibraryTemplate -> configurationFactory.createCargoTestConfiguration()
        is RsCustomTemplate.WasmPackTemplate -> configurationFactory.createWasmPackBuildConfiguration()
        is RsCustomTemplate -> return
    }

    runManager.addConfiguration(configuration)
    runManager.selectedConfiguration = configuration
}

private class DefaultRunConfigurationFactory(val runManager: RunManager, val project: Project) {
    private val cargoProjectName = project.name.replace(' ', '_')

    fun createCargoRunConfiguration(): RunnerAndConfigurationSettings =
        runManager.createConfiguration("Run", CargoCommandConfigurationType.getInstance().factory).apply {
            (configuration as? CargoCommandConfiguration)?.apply {
                command = "run --package $cargoProjectName --bin $cargoProjectName"
                workingDirectory = project.basePath?.toPath()
            }
        }

    fun createCargoTestConfiguration(): RunnerAndConfigurationSettings =
        runManager.createConfiguration("Test", CargoCommandConfigurationType.getInstance().factory).apply {
            (configuration as? CargoCommandConfiguration)?.apply {
                command = "test --package $cargoProjectName --lib tests"
                workingDirectory = project.basePath?.toPath()
            }
        }

    fun createWasmPackBuildConfiguration(): RunnerAndConfigurationSettings =
        runManager.createConfiguration("Build", WasmPackCommandConfigurationType.getInstance().factory).apply {
            (configuration as? WasmPackCommandConfiguration)?.workingDirectory = project.basePath?.toPath()
        }
}
