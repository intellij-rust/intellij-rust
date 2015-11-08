package org.rust.lang.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPatIdent

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?) = when (element) {
        is RustPatIdent -> getQuickNavigateInfo(element)
        else -> null
    }

    private fun getQuickNavigateInfo(element: RustPatIdent): String {
        val location = getLocationString(element)
        val bindingMode = element.bindingMode?.mut?.let { "mut " }.orEmpty()

        return "let $bindingMode<b>${element.identifier.text}</b>$location"
    }

    private fun getLocationString(element: PsiElement?): String {
        return element?.containingFile?.let { " [${it.name}]" }.orEmpty()
    }
}
