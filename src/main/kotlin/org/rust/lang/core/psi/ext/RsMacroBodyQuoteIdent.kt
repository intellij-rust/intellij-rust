/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsMacroBodyQuoteIdent
import org.rust.lang.core.resolve.ref.RsMacroBodyReferenceDelegateImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsMacroBodyQuoteIdentMixin(type: IElementType) : RsElementImpl(type  ), RsMacroBodyQuoteIdent {
    override val referenceNameElement: PsiElement
        get() = quoteIdentifier

    override fun getReference(): RsReference? = RsMacroBodyReferenceDelegateImpl(this)
}
