/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.COLON
import org.rust.lang.core.psi.ext.elementType

class RsCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, RsDeriveCompletionProvider.elementPattern, RsDeriveCompletionProvider)
        extend(CompletionType.BASIC, AttributeCompletionProvider.elementPattern, AttributeCompletionProvider)
        extend(CompletionType.BASIC, RsMacroDefinitionCompletionProvider.elementPattern, RsMacroDefinitionCompletionProvider)
    }

    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean =
        typeChar == ':' && position.elementType == COLON
}
