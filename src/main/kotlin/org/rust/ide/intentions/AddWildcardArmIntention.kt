/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.fixes.AddRemainingArmsFix
import org.rust.ide.fixes.AddWildcardArmFix
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.ext.arms

class AddWildcardArmIntention : AddRemainingArmsIntention() {

    override fun getText(): String = AddWildcardArmFix.NAME

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? =
        super.findApplicableContext(project, editor, element)
            ?.takeIf { it.matchExpr.arms.isNotEmpty() }

    override fun createQuickFix(matchExpr: RsMatchExpr, patterns: List<Pattern>): AddRemainingArmsFix {
        return AddWildcardArmFix(matchExpr)
    }
}
