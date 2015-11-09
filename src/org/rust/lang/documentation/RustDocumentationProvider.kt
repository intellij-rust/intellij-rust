package org.rust.lang.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RustBindingMode
import org.rust.lang.core.psi.RustPatIdent

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is RustPatIdent) {
            val location = getLocationString(element)
            val bindingMode = element.bindingMode?.mut?.let { "mut " }.orEmpty()

            return "let $bindingMode<b>${element.identifier.text}</b>$location"
        }
        return null
    }

    private fun getLocationString(element: PsiElement?): String {
        return element?.containingFile?.let { " [${it.name}]" }.orEmpty()
    }
}
