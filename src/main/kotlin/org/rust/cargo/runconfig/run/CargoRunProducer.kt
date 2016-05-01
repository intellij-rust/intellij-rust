package org.rust.cargo.runconfig.run;

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.impl.RustFnItemImpl

class CargoRunProducer : RunConfigurationProducer<CargoRun>(CargoRunType()) {
    override fun isConfigurationFromContext(configuration: CargoRun?, context: ConfigurationContext?): Boolean {
        return configuration?.name == "Run " + context?.project?.name
    }

    override fun setupConfigurationFromContext(configuration: CargoRun?,
                                               context: ConfigurationContext?,
                                               sourceElement: Ref<PsiElement>?): Boolean {
        if (isMainFile(context?.psiLocation?.containingFile)) {
            configuration?.name = "Run " + context?.project?.name
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

        val declaredFunctions = PsiTreeUtil.findChildrenOfType(file, RustFnItemImpl::class.java)
        for (declaredFunction in declaredFunctions) {
            if (declaredFunction.identifier.text == "main") {
                return true
            }
        }
        return false
    }
}
