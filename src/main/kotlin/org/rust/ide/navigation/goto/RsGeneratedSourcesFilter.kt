/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.findNavigationTargetIfMacroExpansion
import org.rust.lang.core.macros.macroExpansionManager

class RsGeneratedSourcesFilter : GeneratedSourcesFilter() {
    override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
        return project.macroExpansionManager.isExpansionFile(file)
    }

    override fun getOriginalElements(element: PsiElement): List<PsiElement> {
        val result = element.findNavigationTargetIfMacroExpansion() ?: return emptyList()
        return listOf(result)
    }
}
