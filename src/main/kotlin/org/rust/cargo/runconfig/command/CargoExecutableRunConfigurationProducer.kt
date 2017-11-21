/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.mergeWithDefault
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.openapiext.toPsiFile

class CargoExecutableRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val location = context.location ?: return false
        val target = findBinaryTarget(location) ?: return false

        return configuration.canBeFrom(target.cargoCommandLine)
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val target = findBinaryTarget(location) ?: return false
        val fn = location.psiElement.ancestorStrict<RsFunction>()
        val source = if (fn != null && isMainFunction(fn)) fn else context.psiLocation?.containingFile
        sourceElement.set(source)

        configuration.name = target.configurationName
        val cmd = target.cargoCommandLine.mergeWithDefault(configuration)
        configuration.setFromCmd(cmd)
        return true
    }

    private class ExecutableTarget(target: CargoWorkspace.Target) {
        val configurationName: String = "Run ${target.name}"
        val cargoCommandLine = CargoCommandLine.forTarget(target, "run")
    }

    companion object {
        fun isMainFunction(fn: RsFunction): Boolean {
            val ws = fn.cargoWorkspace ?: return false
            return fn.name == "main" && findBinaryTarget(ws, fn.containingFile.virtualFile) != null
        }

        private fun findBinaryTarget(location: Location<*>): ExecutableTarget? {
            val file = location.virtualFile ?: return null
            val rsFile = file.toPsiFile(location.project) as? RsFile ?: return null
            val ws = rsFile.cargoWorkspace ?: return null
            return findBinaryTarget(ws, file)
        }

        private fun findBinaryTarget(ws: CargoWorkspace, file: VirtualFile): ExecutableTarget? {
            val target = ws.findTargetByCrateRoot(file) ?: return null
            if (!target.isBin && !target.isExample) return null
            return ExecutableTarget(target)
        }
    }
}
