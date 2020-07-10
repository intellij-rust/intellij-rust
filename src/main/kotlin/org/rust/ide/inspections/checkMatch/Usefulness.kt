/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.types.ty.*

class Witness(val patterns: MutableList<Pattern> = mutableListOf()) {
    override fun toString() = patterns.toString()

    fun clone(): Witness =
        Witness(patterns.toMutableList())

    fun pushWildConstructor(constructor: Constructor, type: Ty): Witness {
        val subPatternTypes = constructor.subTypes(type)
        for (ty in subPatternTypes) {
            patterns.add(Pattern(ty, PatternKind.Wild))
        }
        return applyConstructor(constructor, type)
    }

    fun applyConstructor(constructor: Constructor, type: Ty): Witness {
        val arity = constructor.arity(type)
        val len = patterns.size
        val oldPatterns = patterns.subList(len - arity, len)
        val pats = oldPatterns.reversed().toList()
        oldPatterns.clear()
        val kind = when (type) {
            is TyAdt -> {
                if (type.item is RsEnumItem) {
                    PatternKind.Variant(
                        type.item,
                        (constructor as Constructor.Variant).variant,
                        pats
                    )
                } else {
                    PatternKind.Leaf(pats)
                }
            }

            is TyTuple -> PatternKind.Leaf(pats)

            is TyReference -> PatternKind.Deref(pats.first())

            is TySlice, is TyArray -> TODO()

            else -> if (constructor is Constructor.ConstantValue) {
                PatternKind.Const(constructor.value)
            } else {
                PatternKind.Wild
            }
        }
        patterns.add(Pattern(type, kind))
        return this
    }
}

sealed class Usefulness {
    class UsefulWithWitness(val witnesses: List<Witness>) : Usefulness() {
        companion object {
            val Empty: UsefulWithWitness get() = UsefulWithWitness(listOf(Witness()))
        }
    }

    object Useful : Usefulness()
    object Useless : Usefulness()

    val isUseful: Boolean get() = this !== Useless
}

/** Use algorithm from [INRIA](http://moscova.inria.fr/~maranget/papers/warn/warn004.html) */
fun isUseful(
    matrix: Matrix,
    patterns: List<Pattern>,
    withWitness: Boolean,
    crateRoot: RsMod?
): Usefulness {
    fun expandConstructors(constructors: List<Constructor>, type: Ty): Usefulness = constructors
        .map { isUsefulSpecialized(matrix, patterns, it, type, withWitness, crateRoot) }
        .find { it.isUseful }
        ?: Usefulness.Useless

    if (patterns.isEmpty()) {
        if (matrix.isEmpty()) {
            return if (withWitness) Usefulness.UsefulWithWitness.Empty else Usefulness.Useful
        }
        return Usefulness.Useless
    }

    val type = matrix.firstColumnType
    val constructors = patterns.first().constructors
    if (constructors != null) {
        return expandConstructors(constructors, type)
    }

    val usedConstructors = matrix.flatMap { it.firstOrNull()?.constructors ?: emptyList() }
    val allConstructors = Constructor.allConstructors(type)
    val missingConstructor = allConstructors.minus(usedConstructors)

    val isPrivatelyEmpty = allConstructors.isEmpty()
    val isDeclaredNonExhaustive = type is TyAdt &&
        type.item.queryAttributes.hasAtomAttribute("non_exhaustive")
    val isInDifferentCrate = type is TyAdt && type.item.crateRoot != crateRoot

    val isNonExhaustive = isPrivatelyEmpty || (isDeclaredNonExhaustive && isInDifferentCrate)

    if (missingConstructor.isEmpty() && !isNonExhaustive) {
        return expandConstructors(allConstructors, type)
    }

    val newMatrix = matrix.mapNotNull {
        val kind = it.firstOrNull()?.kind
        if (kind is PatternKind.Wild || kind is PatternKind.Binding) {
            it.subList(1, it.size)
        } else {
            null
        }
    }
    val newPatterns = patterns.subList(1, patterns.size)
    val res = isUseful(newMatrix, newPatterns, withWitness, crateRoot)
    if (res is Usefulness.UsefulWithWitness) {
        val newWitness = if (isNonExhaustive || usedConstructors.isEmpty()) {
            res.witnesses.map { witness ->
                witness.patterns.add(Pattern(type, PatternKind.Wild))
                witness
            }
        } else {
            res.witnesses.flatMap { witness ->
                missingConstructor.map { witness.clone().pushWildConstructor(it, type) }
            }
        }
        return Usefulness.UsefulWithWitness(newWitness)
    }

    return res
}

private fun isUsefulSpecialized(
    matrix: Matrix,
    patterns: List<Pattern>,
    constructor: Constructor,
    type: Ty,
    withWitness: Boolean,
    crateRoot: RsMod?
): Usefulness {
    val newPatterns = specializeRow(patterns, constructor, type) ?: return Usefulness.Useless
    val newMatrix = matrix.mapNotNull { specializeRow(it, constructor, type) }

    return when (val useful = isUseful(newMatrix, newPatterns, withWitness, crateRoot)) {
        is Usefulness.UsefulWithWitness -> Usefulness.UsefulWithWitness(useful.witnesses.map { it.applyConstructor(constructor, type) })
        else -> useful
    }
}

private fun specializeRow(row: List<Pattern>, constructor: Constructor, type: Ty): List<Pattern>? {
    val pat = row.firstOrNull() ?: return emptyList()
    val wildPatterns = MutableList(constructor.arity(type)) { Pattern.wild(type) }

    val head: List<Pattern>? = when (val kind = pat.kind) {
        is PatternKind.Variant -> {
            if (constructor == pat.constructors?.first()) {
                wildPatterns.apply { fillWithSubPatterns(kind.subPatterns) }
            } else {
                null
            }
        }

        is PatternKind.Leaf -> wildPatterns.apply { fillWithSubPatterns(kind.subPatterns) }

        is PatternKind.Deref -> listOf(kind.subPattern)

        is PatternKind.Const -> when {
            constructor is Constructor.Slice -> TODO()
            constructor.coveredByRange(kind.value, kind.value, true) -> emptyList()
            else -> null
        }

        is PatternKind.Range -> when {
            constructor.coveredByRange(kind.lc, kind.rc, kind.isInclusive) -> emptyList()
            else -> null
        }

        is PatternKind.Slice, is PatternKind.Array -> TODO()

        PatternKind.Wild, is PatternKind.Binding -> wildPatterns
    }

    return head?.plus(row.subList(1, row.size))
}

private fun MutableList<Pattern>.fillWithSubPatterns(subPatterns: List<Pattern>) {
    for ((index, pattern) in subPatterns.withIndex()) {
        while (size <= index) add(Pattern.Wild) // TODO: maybe it's better to throw an exception?
        this[index] = pattern
    }
}
