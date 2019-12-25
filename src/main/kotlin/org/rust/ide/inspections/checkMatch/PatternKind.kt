/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.utils.evaluation.ConstExpr.Value

sealed class PatternKind {
    object Wild : PatternKind()

    /** x, ref x, x @ P, etc */
    data class Binding(val ty: Ty, val name: String) : PatternKind()

    /** Foo(...) or Foo{...} or Foo, where `Foo` is a variant name from an adt with >1 variants (only enums) */
    data class Variant(val item: RsEnumItem, val variant: RsEnumVariant, val subPatterns: List<Pattern>) : PatternKind()

    /** (...), Foo(...), Foo{...}, or Foo, where `Foo` is a variant name from an adt with 1 variant (structs or enums) */
    data class Leaf(val subPatterns: List<Pattern>) : PatternKind()

    /** &P, &mut P, etc */
    data class Deref(val subPattern: Pattern) : PatternKind()

    data class Const(val value: Value<*>) : PatternKind()

    data class Range(val lc: Value<*>, val rc: Value<*>, val isInclusive: Boolean) : PatternKind()


    interface SliceField {
        val prefix: List<Pattern>
        val slice: Pattern?
        val suffix: List<Pattern>
    }

    /**
     * Matches against a slice, checking the length and extracting elements.
     * Irrefutable when there is a slice pattern and both `prefix` and `suffix` are empty
     * e.g. `&[ref xs..]`
     */
    data class Slice(
        override val prefix: List<Pattern>,
        override val slice: Pattern?,
        override val suffix: List<Pattern>
    ) : PatternKind(), SliceField

    /** Fixed match against an array, irrefutable */
    data class Array(
        override val prefix: List<Pattern>,
        override val slice: Pattern?,
        override val suffix: List<Pattern>
    ) : PatternKind(), SliceField
}
