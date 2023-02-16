/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightVirtualFile
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.isGeneratedFile
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.openapiext.testAssert

object PsiModificationUtil {
    fun canReplaceAll(vararg elements: PsiElement): Boolean {
        return elements.all { canReplace(it) }
    }

    fun canReplaceAll(elements: List<PsiElement>): Boolean {
        return elements.all { canReplace(it) }
    }

    fun canReplace(element: PsiElement): Boolean {
        return if (!element.isExpandedFromMacro) {
            isWriteableRegardlessMacros(element)
        } else {
            false
        }
    }

    fun isWriteableRegardlessMacros(element: PsiElement): Boolean {
        testAssert { !element.isExpandedFromMacro }

        // Check if the PSI belongs to the project content roots
        if (!BaseIntentionAction.canModify(element)) return false

        val containingFile = element.containingFile
        val virtualFile = when (val virtualFile = containingFile.virtualFile) {
            null -> return true
            is LightVirtualFile -> virtualFile.originalFile
            is VirtualFileWindow -> virtualFile.delegate
            else -> virtualFile
        } ?: return true

        if (containingFile.project.cargoProjects.isGeneratedFile(virtualFile)) return false

        // Not that we should not check `isWriteable` because it is checked in `ReadonlyStatusHandlerImpl`
        return true
    }
}
