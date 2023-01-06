/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.codeInsight.hints.VcsCodeVisionLanguageContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.RsMacroDefinitionBase
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.openapiext.isUnitTestMode
import java.awt.event.MouseEvent

@Suppress("UnstableApiUsage")
class RsVcsCodeVisionContext : VcsCodeVisionLanguageContext {
    override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) {}

    override fun isAccepted(element: PsiElement): Boolean {
        if (!isUnitTestMode && !Registry.`is`("org.rust.code.vision.author", false)) return false

        return when (element) {
            is RsFunction,
            is RsStructOrEnumItemElement,
            is RsTraitOrImpl,
            is RsMacroDefinitionBase,
            is RsModItem -> true
            else -> false
        }
    }
}
