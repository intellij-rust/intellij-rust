/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.findNavigationTargetIfMacroExpansion
import org.rust.lang.core.macros.macroExpansionManager

class RsGeneratedSourcesFilter : GeneratedSourcesFilter() {
    override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
        return project.macroExpansionManager.isExpansionFile(file) || project.cargoProjects.isGeneratedFile(file)
    }

    override fun getOriginalElements(element: PsiElement): List<PsiElement> {
        if (element is PsiDirectory || element.language != RsLanguage) return emptyList()

        val result = element.findNavigationTargetIfMacroExpansion() ?: return emptyList()
        return listOf(result)
    }
}
