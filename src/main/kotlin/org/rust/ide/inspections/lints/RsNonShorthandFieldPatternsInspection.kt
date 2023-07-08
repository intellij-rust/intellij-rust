/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.RsPatFieldFull
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsVisitor

class RsNonShorthandFieldPatternsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.NonShorthandFieldPatterns

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitPatFieldFull(o: RsPatFieldFull) {
            val identifier = o.identifier?.text ?: return
            val binding = o.pat.text
            if (identifier != binding) return

            holder.registerLintProblem(
                o,
                RsBundle.message("inspection.message.in.this.pattern.redundant", identifier),
                RsLintHighlightingType.WEAK_WARNING,
                listOf(UseShorthandFieldPatternFix(o, identifier))
            )
        }
    }

    override val isSyntaxOnly: Boolean get() = true

    private class UseShorthandFieldPatternFix(
        element: RsPatFieldFull,
        private val identifier: String
    ) : RsQuickFixBase<RsPatFieldFull>(element) {
        override fun getFamilyName(): String = RsBundle.message("intention.family.name.use.shorthand.field.pattern")
        override fun getText(): String = RsBundle.message("intention.name.use.shorthand.field.pattern", identifier)

        override fun invoke(project: Project, editor: Editor?, element: RsPatFieldFull) {
            val patBinding = RsPsiFactory(project).createPatBinding(element.pat.text)
            element.parent.addBefore(patBinding, element)
            element.delete()
        }
    }
}
