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
        extend(CompletionType.BASIC, RsPrimitiveTypeCompletionProvider.elementPattern, RsPrimitiveTypeCompletionProvider)
        extend(CompletionType.BASIC, RsFragmentSpecifierCompletionProvider.elementPattern, RsFragmentSpecifierCompletionProvider)
        extend(CompletionType.BASIC, RsCommonCompletionProvider.elementPattern, RsCommonCompletionProvider)
        extend(CompletionType.BASIC, RsDeriveCompletionProvider.elementPattern, RsDeriveCompletionProvider)
        extend(CompletionType.BASIC, AttributeCompletionProvider.elementPattern, AttributeCompletionProvider)
        extend(CompletionType.BASIC, RsMacroCompletionProvider.elementPattern, RsMacroCompletionProvider)
        extend(CompletionType.BASIC, RsPartialMacroArgumentCompletionProvider.elementPattern, RsPartialMacroArgumentCompletionProvider)
        extend(CompletionType.BASIC, RsFullMacroArgumentCompletionProvider.elementPattern, RsFullMacroArgumentCompletionProvider)
    }

    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean =
        typeChar == ':' && position.elementType == COLON
}
