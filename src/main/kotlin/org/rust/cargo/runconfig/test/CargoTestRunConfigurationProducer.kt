/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.pathAsPath

class CargoTestRunConfigurationProducer : CargoTestRunConfigurationProducerBase() {
    override val commandName: String = "test"

    init {
        registerDirectoryConfigProvider { dir -> createConfigForCargoProject(dir) }
        registerDirectoryConfigProvider { dir -> createConfigForCargoPackage(dir) }
    }

    override fun isSuitable(element: PsiElement): Boolean =
        when (element) {
            is RsMod -> hasTestFunction(element)
            is RsFunction -> element.isTest
            else -> false
        }

    private fun createConfigForCargoProject(dir: PsiDirectory): TestConfig? {
        val dirPath = dir.virtualFile.pathAsPath
        val cargoProject = dir.findCargoProject() ?: return null
        if (dirPath != cargoProject.workingDirectory) return null
        return CargoProjectTestConfig(commandName, dir, cargoProject)
    }

    private fun createConfigForCargoPackage(dir: PsiDirectory): TestConfig? {
        val dirPath = dir.virtualFile.pathAsPath
        val cargoPackage = dir.findCargoPackage() ?: return null
        if (dirPath != cargoPackage.rootDirectory || cargoPackage.origin != PackageOrigin.WORKSPACE) return null
        return CargoPackageTestConfig(commandName, dir, cargoPackage)
    }

    companion object {
        private fun hasTestFunction(mod: RsMod): Boolean =
            mod.processExpandedItemsExceptImpls { it is RsFunction && it.isTest || it is RsMod && hasTestFunction(it) }
    }
}

private class CargoProjectTestConfig(
    override val commandName: String,
    override val sourceElement: PsiDirectory,
    val cargoProject: CargoProject
) : TestConfig {
    override val targets: List<CargoWorkspace.Target> = emptyList()
    override val path: String = ""
    override val exact = false

    override val configurationName: String
        get() = "All ${StringUtil.pluralize(commandName).capitalize()}"

    override fun cargoCommandLine(): CargoCommandLine =
        CargoCommandLine.forProject(cargoProject, commandName)
}

private class CargoPackageTestConfig(
    override val commandName: String,
    override val sourceElement: PsiDirectory,
    val cargoPackage: CargoWorkspace.Package
) : TestConfig {
    override val targets: List<CargoWorkspace.Target> = emptyList()
    override val path: String = ""
    override val exact = false

    override val configurationName: String
        get() = "${StringUtil.pluralize(commandName).capitalize()} in '${sourceElement.name}'"

    override fun cargoCommandLine(): CargoCommandLine =
        CargoCommandLine.forPackage(cargoPackage, commandName)
}
