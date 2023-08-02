/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsMacroReference
import org.rust.lang.core.resolve.ref.RsMacroReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsMacroReferenceImplMixin(type: IElementType) : RsElementImpl(type), RsMacroReference {

    override fun getReference(): RsReference = RsMacroReferenceImpl(this)

    override val referenceName: String
        get() = referenceNameElement.text

    override val referenceNameElement: PsiElement
        get() = metaVarIdentifier
}
