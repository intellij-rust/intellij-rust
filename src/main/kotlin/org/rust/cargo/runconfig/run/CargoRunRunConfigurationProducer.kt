package org.rust.cargo.runconfig.run;

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustFnItem

class CargoRunRunConfigurationProducer
: RunConfigurationProducer<CargoRunRunConfiguration>(CargoRunRunConfigurationType()) {
    override fun isConfigurationFromContext(configuration: CargoRunRunConfiguration,
                                            context: ConfigurationContext): Boolean {
        return configuration.name == "Run " + context.project?.name
    }

    override fun setupConfigurationFromContext(configuration: CargoRunRunConfiguration,
                                               context: ConfigurationContext,
                                               sourceElement: Ref<PsiElement>): Boolean {
        if (isMainFile(context.psiLocation?.containingFile)) {
            configuration.name = "Run " + context.project?.name
            return true
        }
        return false
    }

    private fun isMainFile(file: PsiFile?): Boolean {
        if (file == null) {
            return false
        }

        return file.name == "main.rs" && file.parent?.name == "src" && fileContainsMainFn(file)
    }

    private fun fileContainsMainFn(file: PsiFile?): Boolean {
        if (file == null) {
            return false
        }

        val declaredFunctions = PsiTreeUtil.findChildrenOfType(file, RustFnItem::class.java)
        return declaredFunctions.any { it.name == "main" }
    }
}
