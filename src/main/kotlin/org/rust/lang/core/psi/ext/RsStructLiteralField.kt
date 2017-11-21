/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.RsStructLiteralFieldReferenceImpl


val RsStructLiteralField.parentStructLiteral: RsStructLiteral get() = ancestorStrict<RsStructLiteral>()!!
val RsStructLiteralField.resolveToDeclaration: RsFieldDecl? get() = reference.resolve() as? RsFieldDecl

abstract class RsStructLiteralFieldImplMixin(node: ASTNode) : RsElementImpl(node), RsStructLiteralField {

    override fun getReference(): RsReference = RsStructLiteralFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text
}

