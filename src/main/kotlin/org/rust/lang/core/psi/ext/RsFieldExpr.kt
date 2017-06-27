/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.impl.RsExprImpl
import org.rust.lang.core.resolve.ref.RsFieldExprReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsFieldExprImplMixin(node: ASTNode?) : RsExprImpl(node), RsFieldExpr {
    override val referenceNameElement: PsiElement get() = fieldId

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsFieldExprReferenceImpl(this)

}
