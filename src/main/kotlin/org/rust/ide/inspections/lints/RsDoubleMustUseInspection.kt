/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt

private class FixRemoveMustUseAttr : LocalQuickFix {
    override fun getFamilyName() = RsBundle.message("inspection.DoubleMustUse.FixRemoveMustUseAttr.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        descriptor.psiElement?.delete()
    }
}

/** Analogue of Clippy's double_must_use. */
class RsDoubleMustUseInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.DoubleMustUse

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitFunction(o: RsFunction) {
            super.visitFunction(o)

            val mustUseAttrName = "must_use"
            val attrFunc = o.findFirstMetaItem(mustUseAttrName)
            val type = o.returnType as? TyAdt
            val attrType = type?.item?.findFirstMetaItem(mustUseAttrName)
            if (attrFunc != null && attrType != null) {
                val description = RsBundle.message("inspection.DoubleMustUse.description")
                val highlighting = RsLintHighlightingType.WEAK_WARNING
                holder.registerLintProblem(attrFunc.parent, description, highlighting, fixes = listOf(FixRemoveMustUseAttr()))
            }
        }
    }
}
