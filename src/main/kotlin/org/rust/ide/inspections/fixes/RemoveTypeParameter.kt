/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsElement

class RemoveTypeParameter : LocalQuickFix {

    override fun getName(): String = "Remove all type parameters"

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? RsElement ?: return
        val typeArgumentList = when (element) {
            is RsMethodCall -> element.typeArgumentList
            is RsCallExpr -> (element.expr as RsPathExpr?)?.path?.typeArgumentList
            is RsBaseType -> element.path?.typeArgumentList
            else -> null
        } ?: return

        val lifetimeCount = typeArgumentList.lifetimeList.size
        if (lifetimeCount == 0) {
            typeArgumentList.delete()
        }
    }
}
