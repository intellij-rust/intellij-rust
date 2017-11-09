/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroReference
import org.rust.lang.core.resolve.ref.RsMacroReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsMacroReferenceImplMixin(node: ASTNode) : RsElementImpl(node), RsMacroReference {

    override fun getReference(): RsReference = RsMacroReferenceImpl(this)

    override val referenceName: String
        get() = referenceNameElement.text

    override val referenceNameElement: PsiElement
        get() = macroNameElement(node)!!
}
