package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.ide.utils.presentableQualifiedName
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element !is RsDocAndAttributeOwner) return null

        val name = presentableQualifiedName(element)
        val header = if (name != null) "<pre>$name</pre>\n" else ""
        val functionSignature = (element as? RsFunction)?.presentationInfo?.signatureText
        val signature = if (functionSignature != null) "<pre>$functionSignature</pre>\n" else ""
        val doc = element.documentationAsHtml() ?: ""
        return header + signature + doc
    }

    override fun getQuickNavigateInfo(e: PsiElement, originalElement: PsiElement?): String? =
        (e as? RsNamedElement)?.presentationInfo?.quickDocumentationText
}
