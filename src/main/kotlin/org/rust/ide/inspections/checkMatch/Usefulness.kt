/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.types.ty.*

/*
 * A witness of non-exhaustiveness.
 * At the end of exhaustiveness checking, the witness will have length 1,
 * but in the middle of the algorithm, it can contain multiple patterns.
 */
class Witness(val patterns: MutableList<Pattern> = mutableListOf()) {
    override fun toString() = patterns.toString()

    fun clone(): Witness =
        Witness(patterns.toMutableList())

    fun pushWildConstructor(constructor: Constructor, type: Ty): Witness {
        val subPatternTypes = constructor.subTypes(type)
        for (ty in subPatternTypes) {
            patterns.add(Pattern.wild(ty))
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

/**
 * See detailed description in [rust/src/librustc_mir_build/thir/pattern/_match.rs](https://github.com/rust-lang/rust/blob/dfe1e3b641abbede6230e3931d14f0d43e5b8e54/src/librustc_mir_build/thir/pattern/_match.rs).
 *
 * Original algorithm from [INRIA](http://moscova.inria.fr/~maranget/papers/warn/warn004.html)
 */
fun isUseful(
    matrix: Matrix,
    patterns: List<Pattern>,
    withWitness: Boolean,
    crateRoot: RsMod?,
    isTopLevel: Boolean
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

    // Get the first pattern and analyze it
    val pattern = patterns.first()
    val type = matrix.firstColumnType ?: pattern.ergonomicType
    val constructors = pattern.constructors

    if (constructors != null) {
        /**
         * If [pattern] is a constructor pattern, then its usefulness can be reduced to whether it is useful when
         * we ignore all the patterns in the first column of [matrix] that involve other constructors
         */
        return expandConstructors(constructors, type)
    }

    /**
     * Otherwise, [pattern] is wildcard (or binding which is basically the same).
     * We look at the list of constructors that appear in the first column of [matrix].
     */
    val usedConstructors = matrix.firstColumn.map { it.constructors.orEmpty() }.flatten()
    val allConstructors = Constructor.allConstructors(type)
    val missingConstructors = allConstructors.minus(usedConstructors)

    val isPrivatelyEmpty = allConstructors.isEmpty()
    val isDeclaredNonExhaustive = type is TyAdt &&
        type.item.queryAttributes.hasAtomAttribute("non_exhaustive")
    val isInDifferentCrate = type is TyAdt && type.item.crateRoot != crateRoot

    val isNonExhaustive = isPrivatelyEmpty || (isDeclaredNonExhaustive && isInDifferentCrate)

    if (missingConstructors.isEmpty() && !isNonExhaustive) {
        /**
         * If all possible constructors are present, we must check whether the wildcard [pattern] covers any unmatched value.
         * The wildcard pattern is useful in this case if it is useful when specialized to one of the possible constructors.
         * For example, if `Some(<something>)` and `None` constructors of `Option` are covered, we should specialize
         * wildcard [pattern] to `Some(<something else>)` and check its usefulness
         */
        return expandConstructors(allConstructors, type)
    }

    /**
     * If there are [missingConstructors], then our wildcard [pattern] might be useful.
     * But [missingConstructors] can be matched by wildcards in the beginning of rows, so we need to check
     * usefulness of the remaining patterns in a submatrix containing all rows starting with a wildcard.
     */
    val wildcardRows = matrix.filter { row ->
        when (row.firstOrNull()?.kind) {
            PatternKind.Wild, is PatternKind.Binding -> true
            else -> false
        }
    }
    val wildcardSubmatrix = wildcardRows.map { it.drop(1) }
    val remainingPatterns = patterns.drop(1)

    val res = isUseful(wildcardSubmatrix, remainingPatterns, withWitness, crateRoot, isTopLevel = false)

    if (res is Usefulness.UsefulWithWitness) {
        val reportConstructors = isTopLevel && !type.isIntegral
        val newWitness = if (!reportConstructors && (isNonExhaustive || usedConstructors.isEmpty())) {
            res.witnesses.map { witness ->
                witness.patterns.add(Pattern.wild(type))
                witness
            }
        } else {
            res.witnesses.flatMap { witness ->
                missingConstructors.map { witness.clone().pushWildConstructor(it, type) }
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
    val newMatrix = matrix.mapNotNull { row -> specializeRow(row, constructor, type) }

    return when (val useful = isUseful(newMatrix, newPatterns, withWitness, crateRoot, isTopLevel = false)) {
        is Usefulness.UsefulWithWitness -> Usefulness.UsefulWithWitness(useful.witnesses.map { it.applyConstructor(constructor, type) })
        else -> useful
    }
}

private fun specializeRow(row: List<Pattern>, constructor: Constructor, type: Ty): List<Pattern>? {
    val firstPattern = row.firstOrNull() ?: return emptyList()
    val wildPatterns = constructor
        .subTypes(type)
        .map { subType -> Pattern.wild(subType) }
        .toMutableList()

    val head: List<Pattern>? = when (val kind = firstPattern.kind) {
        is PatternKind.Variant -> {
            if (constructor == firstPattern.constructors?.first()) {
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
        while (size <= index) add(Pattern.wild()) // TODO: maybe it's better to throw an exception?
        this[index] = pattern
    }
}
