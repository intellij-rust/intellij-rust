/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

typealias TypeFlags = Int
const val HAS_TY_INFER_MASK: Int = 1
const val HAS_TY_TYPE_PARAMETER_MASK: Int = 2
const val HAS_TY_PROJECTION_MASK: Int = 4
const val HAS_RE_EARLY_BOUND_MASK: Int = 8

/**
 * An entity in the Rust typesystem, which can be one of
 * several kinds (only types and lifetimes for now).
 */
abstract class Kind(val flags: TypeFlags = 0)

fun mergeFlags(kinds: Collection<Kind>): TypeFlags = kinds.fold(0) { a, b -> a or b.flags }

fun mergeFlags(element: BoundElement<*>): TypeFlags =
    mergeFlags(element.subst.kinds) or mergeFlags(element.assoc.values)
