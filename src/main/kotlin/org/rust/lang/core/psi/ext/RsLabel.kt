/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.resolve.ref.RsLabelReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsLabelImplMixin (node: ASTNode) : RsElementImpl(node), RsLabel {

    override val referenceNameElement: PsiElement get() = quoteIdentifier

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsLabelReferenceImpl(this)

}
