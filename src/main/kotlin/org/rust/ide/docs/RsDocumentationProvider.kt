/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.ide.utils.escaped
import org.rust.ide.utils.presentableQualifiedName
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.types.infer.inferDeclarationType
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is RsDocAndAttributeOwner -> generateDoc(element)
        is RsPatBinding -> generateDoc(element)?.let { "<pre>$it</pre>" }
        else -> null
    }

    private fun generateDoc(element: RsDocAndAttributeOwner): String? {
        val doc = element.documentationAsHtml() ?: ""
        return element.header + element.signature + doc
    }

    private fun generateDoc(element: RsPatBinding): String? {
        val presentationInfo = element.presentationInfo ?: return null
        val type = inferDeclarationType(element).toString().escaped
        return "${presentationInfo.type} <b>${presentationInfo.name}</b>: $type"
    }

    override fun getQuickNavigateInfo(e: PsiElement, originalElement: PsiElement?): String? = when (e) {
        is RsPatBinding -> generateDoc(e)
        is RsNamedElement -> e.presentationInfo?.quickDocumentationText
        else -> null
    }
}

private val RsDocAndAttributeOwner.header: String get() {
    val rawHeader = when (this) {
        is RsFieldDecl -> {
            val fieldOwner = parent?.parent as? RsDocAndAttributeOwner
            if (fieldOwner != null) presentableQualifiedName(fieldOwner) else null
        }
        else -> presentableQualifiedName(this)
    }
    return if (rawHeader != null) "<pre>$rawHeader</pre>\n" else ""
}

private val RsDocAndAttributeOwner.signature: String get() {
    val rawSignature = when (this) {
        // unfortunately, Kotlin compiler doesn't allow to call presentationInfo
        // after check for RsFunction and RsFieldDecl in one branch
        // although RsFunction and RsFieldDecl are both RsNamedElement
        is RsFunction -> presentationInfo?.signatureText
        is RsFieldDecl -> presentationInfo?.signatureText
        else -> null
    }
    return if (rawSignature != null) "<pre>$rawSignature</pre>\n" else ""
}
