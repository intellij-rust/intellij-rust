/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.resolve.ref.RsFieldLookupReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsFieldLookupImplMixin(type: IElementType) : RsElementImpl(type), RsFieldLookup {
    override val referenceNameElement: PsiElement get() = (identifier ?: integerLiteral)!!

    override fun getReference(): RsReference = RsFieldLookupReferenceImpl(this)
}

val RsFieldLookup.isAsync: Boolean
    get() = this.text == "await"
