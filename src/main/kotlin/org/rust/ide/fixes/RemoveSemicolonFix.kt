/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsExprStmt

class RemoveSemicolonFix : LocalQuickFix {
    override fun getName() = "Remove semicolon"

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val statement = (descriptor.psiElement as RsExprStmt)
        statement.semicolon?.delete()
    }
}
