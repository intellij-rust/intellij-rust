/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
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
