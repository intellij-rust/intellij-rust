/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.RsStructLiteralFieldReferenceImpl


val RsStructLiteralField.parentStructLiteral: RsStructLiteral get() = ancestorStrict()!!

inline fun <reified T : RsElement> RsStructLiteralField.resolveToElement(): T? =
    reference.multiResolve().filterIsInstance<T>().singleOrNull()

fun RsStructLiteralField.resolveToDeclaration(): RsFieldDecl? = resolveToElement()
fun RsStructLiteralField.resolveToBinding(): RsPatBinding? = resolveToElement()

abstract class RsStructLiteralFieldImplMixin(node: ASTNode) : RsElementImpl(node), RsStructLiteralField {

    override fun getReference(): RsReference = RsStructLiteralFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier ?: integerLiteral!!
}

