package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.RsDocAndAttributeOwner
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMod
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element !is RsDocAndAttributeOwner) return null

        val name = if (element is RsMod) element.modName else element.name
        val header = if (name != null) "<pre>$name</pre>\n" else ""
        val functionSignature = (element as? RsFunction)?.presentationInfo?.declarationText
        val signature = if (functionSignature != null) "<pre>$functionSignature</pre>\n" else ""
        val doc = element.documentationAsHtml() ?: ""
        return header + signature + doc
    }

    override fun getQuickNavigateInfo(e: PsiElement, originalElement: PsiElement?): String? =
        if (e is RsNamedElement) {
            e.presentationInfo.quickDocumentationText
        } else {
            null
        }
}
