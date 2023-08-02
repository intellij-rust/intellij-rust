/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsMacroBodyIdent
import org.rust.lang.core.resolve.ref.RsMacroBodyReferenceDelegateImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsMacroBodyIdentMixin(type: IElementType) : RsElementImpl(type), RsMacroBodyIdent {
    override val referenceNameElement: PsiElement
        get() = identifier

    override fun getReference(): RsReference? = RsMacroBodyReferenceDelegateImpl(this)
}
