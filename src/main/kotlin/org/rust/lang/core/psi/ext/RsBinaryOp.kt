/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryOp
import org.rust.lang.core.resolve.ref.RsBinaryOpReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsBinaryOpImplMixin(node: ASTNode) : RsElementImpl(node), RsBinaryOp {

    override val referenceNameElement: PsiElement get() = operator

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsBinaryOpReferenceImpl(this)
}
