/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.customBuild

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoRunConfigurationProducer
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.openapiext.toPsiFile

// This producer should be registered not through CargoRunConfigurationProducer but on its own
// because build.rs files are a special case.
class CustomBuildRunConfigurationProducer: CargoRunConfigurationProducer() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return CustomBuildConfigurationType.getInstance().factory
    }

    override fun isConfigurationFromContext(configuration: CargoCommandConfiguration, context: ConfigurationContext): Boolean {
        if (configuration !is CustomBuildConfiguration) return false
        val location = context.location ?: return false
        val target = findCustomBuildTarget(location) ?: return false

        return configuration.canBeFrom(target)
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        if (configuration !is CustomBuildConfiguration) return false

        val location = context.location ?: return false
        val target = findCustomBuildTarget(location) ?: return false
        val fn = location.psiElement.ancestorStrict<RsFunction>()
        val source = if (fn != null && isBuildScriptMainFunction(fn)) fn else context.psiLocation?.containingFile
        sourceElement.set(source)

        configuration.setTarget(target);

        return true
    }

    companion object {
        fun isBuildScriptMainFunction(fn: RsFunction): Boolean {
            val ws = fn.cargoWorkspace ?: return false
            return fn.parent is RsFile && fn.name == "main" && findCustomBuildTarget(ws, fn.containingFile.virtualFile) != null
        }

        private fun findCustomBuildTarget(location: Location<*>): CargoWorkspace.Target? {
            val file = location.virtualFile ?: return null
            val rsFile = file.toPsiFile(location.project) as? RsFile ?: return null
            val ws = rsFile.cargoWorkspace ?: return null
            return findCustomBuildTarget(ws, file)
        }

        private fun findCustomBuildTarget(ws: CargoWorkspace, file: VirtualFile): CargoWorkspace.Target? {
            val target = ws.findTargetByCrateRoot(file) ?: return null
            if (!target.kind.isCustomBuild) return null
            return target
        }
    }
}
