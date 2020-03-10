/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.constParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter

/**
 * Represents a potentially generic Psi Element, like `fn make_t<T>() { }`,
 * together with actual type arguments, like `T := i32` ([subst]) and
 * associated type values ([assoc]).
 */
data class BoundElement<out E : RsElement>(
    val element: E,
    /**
     * Generic type bindings, e.g. if we have some generic declaration
     * `struct S<T>(T)` and its usage with concrete type parameter value
     * `let _: S<u8>;`, [subst] maps `T` to `u8`
     */
    val subst: Substitution = emptySubstitution,
    /**
     * Associated type bindings, e.g. if we have some trait with
     * associated type `trait Trait { type Item; }` and its usage
     * with concrete associated type value `type T = Trait<Item=u8>`,
     * [assoc] maps `Item` to `u8`
     */
    val assoc: Map<RsTypeAlias, Ty> = emptyMap()
) : ResolveResult, TypeFoldable<BoundElement<E>> {
    override fun getElement(): PsiElement = element
    override fun isValidResult(): Boolean = true

    inline fun <reified T : RsElement> downcast(): BoundElement<T>? =
        if (element is T) BoundElement(element, subst, assoc) else null

    override fun superFoldWith(folder: TypeFolder): BoundElement<E> =
        BoundElement(
            element,
            this.subst.foldValues(folder),
            assoc.mapValues { (_, value) -> value.foldWith(folder) }
        )

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        assoc.values.any { visitor.visitTy(it) } ||
            subst.types.any { it.visitWith(visitor) } ||
            subst.regions.any { it.visitWith(visitor) } ||
            subst.consts.any { it.visitWith(visitor) }
}

val BoundElement<RsGenericDeclaration>.positionalTypeArguments: List<Ty>
    get() = element.typeParameters.map { subst[it] ?: TyTypeParameter.named(it) }

val BoundElement<RsGenericDeclaration>.positionalConstArguments: List<Const>
    get() = element.constParameters.map { subst[it] ?: CtConstParameter(it) }
