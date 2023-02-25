/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.ide.annotator.fixes.NameSuggestionFix
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.stringValue

class RsUnknownCrateTypesInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnknownCrateTypes

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitLitExpr(element: RsLitExpr) {
                if (!RsPsiPattern.insideCrateTypeAttrValue.accepts(element)) return

                val elementValue = element.stringValue ?: return
                if (elementValue !in KNOWN_CRATE_TYPES) {
                    val fixes = NameSuggestionFix.createApplicable(
                        element, elementValue, KNOWN_CRATE_TYPES, 1
                    ) { RsPsiFactory(element.project).createExpression("\"$it\"") }

                    holder.registerLintProblem(element, "Invalid `crate_type` value", fixes = fixes)
                }
            }
        }

    companion object {
        val KNOWN_CRATE_TYPES = listOf("bin", "lib", "dylib", "staticlib", "cdylib", "rlib", "proc-macro")
    }
}
