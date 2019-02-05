/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.namedFields
import org.rust.lang.core.psi.ext.positionalFields
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.substitute
import org.rust.stdext.dequeOf
import java.util.*

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

/**
 * See [org.rust.lang.core.type.RsImplicitTraitsTest]
 */
tailrec fun Ty.isSized(): Boolean {
    return when (this) {
        is TyNumeric,
        is TyBool,
        is TyChar,
        is TyUnit,
        is TyNever,
        is TyReference,
        is TyPointer,
        is TyArray,
        is TyFunction -> true
        is TyStr, is TySlice, is TyTraitObject -> false
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

val Ty.isSelf: Boolean
    get() = this is TyTypeParameter && this.parameter is TyTypeParameter.Self

fun Ty.walk(): TypeIterator = TypeIterator(this)

class TypeIterator(root: Ty) : Iterator<Ty> {
    private val stack: Deque<Ty> = dequeOf(root)
    private var lastSubtreeSize: Int = 0

    override fun hasNext(): Boolean = stack.isNotEmpty()

    override fun next(): Ty {
        val ty = stack.removeFirst()
        lastSubtreeSize = stack.size
        pushSubTypes(stack, ty)
        return ty
    }

    fun skipCurrentSubtree() {
        while (stack.size > lastSubtreeSize) {
            stack.removeLast()
        }
    }
}

fun Ty.walkShallow(): Iterator<Ty> {
    val stack = dequeOf<Ty>()
    pushSubTypes(stack, this)
    return stack.iterator()
}

private fun pushSubTypes(stack: Deque<Ty>, parentTy: Ty) {
    when (parentTy) {
        is TyAdt ->
            stack.addAll(parentTy.typeArguments)
        is TyAnon, is TyProjection ->
            stack.addAll(parentTy.typeParameterValues.types)
        is TyArray ->
            stack.add(parentTy.base)
        is TyPointer ->
            stack.add(parentTy.referenced)
        is TyReference ->
            stack.add(parentTy.referenced)
        is TySlice ->
            stack.add(parentTy.elementType)
        is TyTraitObject ->
            stack.addAll(parentTy.trait.subst.types)
        is TyTuple ->
            stack.addAll(parentTy.types)
        is TyFunction -> {
            stack.addAll(parentTy.paramTypes)
            stack.add(parentTy.retType)
        }
    }
}

fun Ty.builtinDeref(explicit: Boolean = true): Pair<Ty, Mutability>? =
    when {
        this is TyReference -> Pair(referenced, mutability)
        this is TyPointer && explicit -> Pair(referenced, mutability)
        else -> null
    }

/**
 * TODO:
 * There are some problems with `Self` inference (e.g. https://github.com/intellij-rust/intellij-rust/issues/2530)
 * so for now just assume `Self` is always copyable
 */
fun Ty.isMovesByDefault(lookup: ImplLookup): Boolean =
    when (this) {
        is TyUnknown, is TyReference, is TyPointer, is TyFunction -> false
        is TyTuple -> types.any { it.isMovesByDefault(lookup) }
        is TyArray -> base.isMovesByDefault(lookup)
        is TyTypeParameter -> !(parameter == TyTypeParameter.Self || lookup.isCopy(this))
        else -> !lookup.isCopy(this)
    }

val Ty.isBox: Boolean
    get() = this is TyAdt && item == item.knownItems.Box
