/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsStructLiteralField

class ChangeToFieldShorthandFix : LocalQuickFix {
    override fun getFamilyName(): String = "Use initialization shorthand"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        applyShorthandInit(descriptor.psiElement as RsStructLiteralField)
    }

    companion object {
        fun applyShorthandInit(field: RsStructLiteralField) {
            field.expr?.delete()
            field.colon?.delete()
        }
    }
}
