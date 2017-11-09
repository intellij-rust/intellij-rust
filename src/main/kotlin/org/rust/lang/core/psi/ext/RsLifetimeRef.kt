/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.resolve.ref.RsLifetimeReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.refactoring.RsNamesValidator


val RsLifetime.isPredefined : Boolean get() = quoteIdentifier.text in RsNamesValidator.PredefinedLifetimes

abstract class RsLifetimeImplMixin (node: ASTNode) : RsElementImpl(node), RsLifetime {

    override val referenceNameElement: PsiElement get() = quoteIdentifier

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsLifetimeReferenceImpl(this)
}
