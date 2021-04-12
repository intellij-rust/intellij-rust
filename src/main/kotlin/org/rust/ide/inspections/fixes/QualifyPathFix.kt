/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory


/**
 * Fix that qualifies a path.
 */
class QualifyPathFix(
    path: RsPath,
    private val qualifiedPath: String
) : LocalQuickFixOnPsiElement(path) {
    private val _text: String = "Qualify path to `$qualifiedPath`"

    override fun getText() = _text
    override fun getFamilyName() = "Qualify path"

    override fun invoke(project: Project, file: PsiFile, expr: PsiElement, endElement: PsiElement) {
        val path = expr as? RsPath ?: return
        val fullPath = "$qualifiedPath${path.typeArgumentList?.text.orEmpty()}"
        val newPath = RsPsiFactory(project).tryCreatePath(fullPath) ?: return
        path.replace(newPath)
    }
}
