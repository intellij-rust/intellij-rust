/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.dyn
import org.rust.lang.core.psi.ext.isEdition2018
import org.rust.lang.core.resolve.ref.deepResolve

class RsTraitObjectInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : RsVisitor() {
        override fun visitTypeReference(typeReference: RsTypeReference) {
            if (!typeReference.isEdition2018) return

            if (typeReference.baseType?.path?.reference?.deepResolve() is RsTraitItem
                || typeReference.traitType?.run { dyn == null && impl == null } == true
            ) {
                holder.registerProblem(
                    typeReference,
                    "Trait objects without explicit 'dyn' are deprecated",
//                    ProblemHighlightType.WEAK_WARNING,
                    object : LocalQuickFix {
                        override fun getFamilyName(): String = "Add dyn keyword to trait object"

                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            val target = descriptor.psiElement as RsTypeReference
                            val traitText = target.baseType?.path?.text
                                ?: target.traitType!!.text
                            val new = RsPsiFactory(project).createDynTraitType(traitText)
                            target.firstChild.replace(new)
                        }
                    })

            }
        }
    }
}
