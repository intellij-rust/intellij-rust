package org.rust.cargo.runconfig.test;

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.impl.RustFnItemImpl

class CargoTestProducer : RunConfigurationProducer<CargoTest>(CargoTestType()) {
    override fun isConfigurationFromContext(configuration: CargoTest?, context: ConfigurationContext?): Boolean {
        return configuration?.name == configurationName(context)
    }

    override fun setupConfigurationFromContext(configuration: CargoTest?,
                                               context: ConfigurationContext?,
                                               sourceElement: Ref<PsiElement>?): Boolean {
        if (fileContainsTests(context?.psiLocation?.containingFile)) {
            val fileName = getFileName(context)
            configuration?.name = configurationName(context)
            configuration?.testName = fileName?.dropLast(3) ?: ""
            return true
        }
        return false
    }

    private fun configurationName(context: ConfigurationContext?) = "All tests in " + getFileName(context)

    private fun getFileName(context: ConfigurationContext?): String? {
        val fileName = context?.psiLocation?.containingFile?.name
        return fileName
    }

    private fun fileContainsTests(file: PsiFile?): Boolean {
        if (file == null) {
            return false
        }

        val declaredFunctions = PsiTreeUtil.findChildrenOfType(file, RustFnItemImpl::class.java)
        for (declaredFunction in declaredFunctions) {
            if (isTestFunction(declaredFunction)) {
                return true
            }
        }
        return false
    }

    private fun isTestFunction(function: RustFnItemImpl): Boolean {
        if (!function.outerAttrList.isEmpty()) {
            for (rustOuterAttr in function.outerAttrList) {
                if (rustOuterAttr?.metaItem?.text == "test") {
                    return true;
                }
            }
        }
        return false
    }
}
