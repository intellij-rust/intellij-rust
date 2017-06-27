/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.RsConstants
import org.rust.lang.core.psi.rustMod
import javax.swing.Icon

class RsIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? = when (element) {
        is PsiFile -> getFileIcon(element)
        else -> null
    }

    private fun getFileIcon(element: PsiFile): Icon? = when {
        element.name == RsConstants.MOD_RS_FILE -> RsIcons.MOD_RS
        isMainFile(element) -> RsIcons.MAIN_RS
        else -> null
    }

    private fun isMainFile(element: PsiFile) =
        (element.name == RsConstants.MAIN_RS_FILE || element.name == RsConstants.LIB_RS_FILE)
            && element.rustMod?.isCrateRoot ?: false
}
