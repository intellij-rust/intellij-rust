package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.ide.utils.escaped
import org.rust.ide.utils.presentableQualifiedName
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.types.infer.inferDeclarationType
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is RsDocAndAttributeOwner -> generateDoc(element)
        is RsPatBinding -> generateDoc(element)
        else -> null
    }

    private fun generateDoc(element: RsDocAndAttributeOwner): String? {
        val name = presentableQualifiedName(element)
        val header = if (name != null) "<pre>$name</pre>\n" else ""
        val functionSignature = (element as? RsFunction)?.presentationInfo?.signatureText
        val signature = if (functionSignature != null) "<pre>$functionSignature</pre>\n" else ""
        val doc = element.documentationAsHtml() ?: ""
        return header + signature + doc
    }

    private fun generateDoc(element: RsPatBinding): String? {
        val presentationInfo = element.presentationInfo ?: return null
        val type = inferDeclarationType(element).toString().escaped
        return "${presentationInfo.type} <b>${presentationInfo.name}</b>: $type"
    }

    override fun getQuickNavigateInfo(e: PsiElement, originalElement: PsiElement?): String? =
        (e as? RsNamedElement)?.presentationInfo?.quickDocumentationText
}
