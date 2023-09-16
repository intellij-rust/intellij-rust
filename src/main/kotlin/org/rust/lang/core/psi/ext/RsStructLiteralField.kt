/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
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

/**
 * ```
 * struct S {
 *     foo: i32,
 *     bar: i32,
 * }
 * fn main() {
 *     let foo = 1;
 *     let s = S {
 *         foo,   // isShorthand = true
 *         bar: 1 // isShorthand = false
 *     };
 * }
 * ```
 */
val RsStructLiteralField.isShorthand: Boolean get() = colon == null

abstract class RsStructLiteralFieldImplMixin(type: IElementType) : RsElementImpl(type), RsStructLiteralField {

    override fun getReference(): RsReference = RsStructLiteralFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier ?: integerLiteral!!
}

