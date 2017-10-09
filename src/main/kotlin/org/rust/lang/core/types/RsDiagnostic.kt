/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.rust.lang.core.types.RsErrorCode.E0308
import org.rust.lang.core.types.Severity.*
import org.rust.lang.core.types.ty.Ty

sealed class RsDiagnostic(val element: PsiElement, val experimental: Boolean = false) {
    abstract fun prepare(): PreparedAnnotation

    class TypeError(
        element: PsiElement,
        private val expectedTy: Ty,
        private val actualTy: Ty
    ) : RsDiagnostic(element, experimental = true) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0308,
            "mismatched types",
            expectedFound(expectedTy, actualTy)
        )
    }
}

enum class RsErrorCode {
    E0308;

    val code: String
        get() = toString()
    val infoUrl: String
        get() = "https://doc.rust-lang.org/error-index.html#$code"
}

enum class Severity {
    INFO, WARN, ERROR
}

class PreparedAnnotation(
    val severity: Severity,
    val errorCode: RsErrorCode,
    val header: String,
    val description: String,
    val fixes: List<LocalQuickFix> = emptyList()
)

fun RsDiagnostic.addToHolder(holder: AnnotationHolder) {
    val prepared = prepare()
    val ann = holder.createAnnotation(
        prepared.severity.toHighlightSeverity(),
        element.textRange,
        simpleHeader(prepared.errorCode, prepared.header),
        "<html>${htmlHeader(prepared.errorCode, prepared.header)}<br>${prepared.description}</html>"
    )

    for (fix in prepared.fixes) {
        if (fix is IntentionAction) {
            ann.registerFix(fix)
        } else {
            val descriptor = InspectionManager.getInstance(element.project)
                .createProblemDescriptor(element, ann.message, fix, prepared.severity.toProblemHighlightType(), true)

            ann.registerFix(fix, null, null, descriptor)
        }
    }
}

fun RsDiagnostic.addToHolder(holder: ProblemsHolder) {
    val prepared = prepare()
    holder.registerProblem(
        element,
        "<html>${htmlHeader(prepared.errorCode, prepared.header)}<br>${prepared.description}</html>",
        prepared.severity.toProblemHighlightType(),
        *prepared.fixes.toTypedArray()
    )
}

private fun Severity.toProblemHighlightType(): ProblemHighlightType = when (this) {
    INFO -> ProblemHighlightType.INFORMATION
    WARN -> ProblemHighlightType.WEAK_WARNING
    ERROR -> ProblemHighlightType.ERROR
}

private fun Severity.toHighlightSeverity(): HighlightSeverity = when (this) {
    INFO -> HighlightSeverity.INFORMATION
    WARN -> HighlightSeverity.WARNING
    ERROR -> HighlightSeverity.ERROR
}

private fun simpleHeader(error: RsErrorCode, description: String): String =
    "$description ${error.code}"

private fun htmlHeader(error: RsErrorCode, description: String): String =
    "$description <a href='${error.infoUrl}'>${error.code}</a>"

private fun expectedFound(expectedTy: Ty, actualTy: Ty): String {
    val expectedTyS = escapeString(expectedTy.toString())
    val actualTyS = escapeString(actualTy.toString())
    return "expected $expectedTyS, found $actualTyS"
}

