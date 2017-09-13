/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.rust.lang.core.types.RsErrorCode.E0308
import org.rust.lang.core.types.ty.Ty

sealed class RsDiagnostic(val element: PsiElement) {
    abstract fun addToHolder(holder: AnnotationHolder)

    class TypeError(
        element: PsiElement,
        private val expectedTy: Ty,
        private val actualTy: Ty
    ) : RsDiagnostic(element) {
        override fun addToHolder(holder: AnnotationHolder) {
            holder.createErrorAnnotation(
                element,
                header(E0308, "mismatched types"),
                expectedFound(expectedTy, actualTy)
            )
        }
    }
}

private class AnnotationHeader(
    val plainText: String,
    val html: String
)

private enum class RsErrorCode {
    E0308;

    val code: String
        get() = toString()
    val infoUrl: String
        get() = "https://doc.rust-lang.org/error-index.html#$code"
}

private fun header(error: RsErrorCode, description: String): AnnotationHeader =
    AnnotationHeader("$description ${error.code}", "$description <a href='${error.infoUrl}'>${error.code}</a>")

private fun expectedFound(expectedTy: Ty, actualTy: Ty): String {
    val expectedTyS = escapeString(expectedTy.toString())
    val actualTyS = escapeString(actualTy.toString())
    return "expected $expectedTyS, found $actualTyS"
}


private fun AnnotationHolder.createErrorAnnotation(
    element: PsiElement,
    header: AnnotationHeader,
    detailsHtml: String
): Annotation {
    return createAnnotation(HighlightSeverity.ERROR,
        element.textRange,
        header.plainText,
        "<html>${header.html}<br>$detailsHtml</html>"
    )
}

