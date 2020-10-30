/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.import.ImportInfo
import org.rust.ide.utils.import.insertExternCrateIfNeeded
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory

/**
 * Fix that qualifies a path.
 */
class QualifyPathFix(
    path: RsPath,
    private val importInfo: ImportInfo
) : LocalQuickFixOnPsiElement(path) {
    override fun getText(): String = "Qualify path to `${importInfo.usePath}`"
    override fun getFamilyName(): String = "Qualify path"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val path = startElement as? RsPath ?: return
        val qualifiedPath = importInfo.usePath
        val fullPath = "$qualifiedPath${path.typeArgumentList?.text.orEmpty()}"
        val newPath = RsPsiFactory(project).tryCreatePath(fullPath) ?: return

        importInfo.insertExternCrateIfNeeded(path)
        path.replace(newPath)
    }
}
