/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.type

open class AddRemainingArmsFix(match: RsMatchExpr, val patterns: List<Pattern>) : LocalQuickFixOnPsiElement(match) {
    override fun getFamilyName(): String = RsBundle.message("inspection.AddRemainingArms.Fix.name")
    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsMatchExpr) return
        val expr = startElement.expr ?: return

        val rsPsiFactory = RsPsiFactory(project)
        val oldMatchBody = startElement.matchBody
            ?: rsPsiFactory.createMatchBody(emptyList()).let { startElement.addAfter(it, expr) as RsMatchBody }

        val lastMatchArm = oldMatchBody.matchArmList.lastOrNull()
        if (lastMatchArm != null && lastMatchArm.expr !is RsBlockExpr && lastMatchArm.comma == null)
            lastMatchArm.add(rsPsiFactory.createComma())

        val newArms = createNewArms(rsPsiFactory, oldMatchBody)
        for (arm in newArms) {
            oldMatchBody.addBefore(arm, oldMatchBody.rbrace)
        }

        importTypeReferencesFromTy(startElement, expr.type)
    }

    open fun createNewArms(psiFactory: RsPsiFactory, oldMatchBody: RsMatchBody): List<RsMatchArm> =
        psiFactory.createMatchBody(patterns, oldMatchBody).matchArmList
}

class AddWildcardArmFix(match: RsMatchExpr) : AddRemainingArmsFix(match, emptyList()) {
    override fun getFamilyName(): String = RsBundle.message("inspection.AddWildcardArm.Fix.text")
    override fun getText(): String = familyName

    override fun createNewArms(psiFactory: RsPsiFactory, oldMatchBody: RsMatchBody): List<RsMatchArm> = listOf(
        psiFactory.createMatchBody(listOf(Pattern.wild())).matchArmList.first()
    )
}
