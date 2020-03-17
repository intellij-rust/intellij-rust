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
        extend(CompletionType.BASIC, RsPrimitiveTypeCompletionProvider)
        extend(CompletionType.BASIC, RsFragmentSpecifierCompletionProvider)
        extend(CompletionType.BASIC, RsCommonCompletionProvider)
        extend(CompletionType.BASIC, RsTupleFieldCompletionProvider)
        extend(CompletionType.BASIC, RsDeriveCompletionProvider)
        extend(CompletionType.BASIC, RsAttributeCompletionProvider)
        extend(CompletionType.BASIC, RsMacroCompletionProvider)
        extend(CompletionType.BASIC, RsPartialMacroArgumentCompletionProvider)
        extend(CompletionType.BASIC, RsFullMacroArgumentCompletionProvider)
        extend(CompletionType.BASIC, RsCfgAttributeCompletionProvider)
    }

    fun extend(type: CompletionType?, provider: RsCompletionProvider) {
        extend(type, provider.elementPattern, provider)
    }

    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean =
        typeChar == ':' && position.elementType == COLON
}
