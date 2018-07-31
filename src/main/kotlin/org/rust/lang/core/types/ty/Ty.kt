/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.namedFields
import org.rust.lang.core.psi.ext.positionalFields
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.substitute

/**
 * Represents both a type, like `i32` or `S<Foo, Bar>`, as well
 * as an unbound constructor `S`.
 *
 * The name `Ty` is short for `Type`, inspired by the Rust
 * compiler.
 */
abstract class Ty(override val flags: TypeFlags = 0) : Kind, TypeFoldable<Ty> {

    override fun foldWith(folder: TypeFolder): Ty = folder.foldTy(this)

    override fun superFoldWith(folder: TypeFolder): Ty = this

    override fun visitWith(visitor: TypeVisitor): Boolean = visitor.visitTy(this)

    override fun superVisitWith(visitor: TypeVisitor): Boolean = false

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    open val typeParameterValues: Substitution get() = emptySubstitution

    /**
     * User visible string representation of a type
     */
    final override fun toString(): String = tyToString(this)
}

enum class Mutability {
    MUTABLE,
    IMMUTABLE;

    val isMut: Boolean get() = this == MUTABLE

    companion object {
        fun valueOf(mutable: Boolean): Mutability =
            if (mutable) MUTABLE else IMMUTABLE

        val DEFAULT_MUTABILITY = MUTABLE
    }
}

fun Ty.getTypeParameter(name: String): TyTypeParameter? {
    return typeParameterValues.typeParameterByName(name)
}

tailrec fun Ty.isSized(): Boolean {
    return when (this) {
        is TyPrimitive,
        is TyReference,
        is TyPointer,
        is TyArray,
        is TyFunction -> true
        is TySlice, is TyTraitObject -> false
        is TyTypeParameter -> isSized
        is TyAdt -> {
            val item = item as? RsStructItem ?: return true
            val namedFields = item.namedFields
            val tupleFields = item.positionalFields
            val typeRef = when {
                namedFields.isNotEmpty() -> namedFields.last().typeReference
                tupleFields.isNotEmpty() -> tupleFields.last().typeReference
                else -> null
            }
            val type = typeRef?.type?.substitute(typeParameterValues) ?: return true
            type.isSized()
        }
        is TyTuple -> types.last().isSized()
        else -> true
    }
}
