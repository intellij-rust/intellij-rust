/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.resolve.ref.RsFieldLookupReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference


val RsFieldLookup.parentDotExpr: RsDotExpr get() = parent as RsDotExpr
val RsFieldLookup.receiver: RsExpr get() = parentDotExpr.expr

abstract class RsFieldLookupImplMixin(node: ASTNode) : RsElementImpl(node), RsFieldLookup {
    override val referenceNameElement: PsiElement get() = (identifier ?: integerLiteral)!!

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsFieldLookupReferenceImpl(this)
}

