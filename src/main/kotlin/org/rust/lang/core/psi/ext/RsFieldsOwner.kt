/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBlockFields
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.RsTupleFields

interface RsFieldsOwner : RsElement{
    val blockFields: RsBlockFields?
    val tupleFields: RsTupleFields?
}

val RsFieldsOwner.namedFields: List<RsFieldDecl>
    get() = blockFields?.fieldDeclList.orEmpty()

val RsFieldsOwner.positionalFields: List<RsTupleFieldDecl>
    get() = tupleFields?.tupleFieldDeclList.orEmpty()

/**
 * If some field of a struct/enum is private (not visible from [mod]),
 * it isn't possible to instantiate it at [mod] anyhow.
 * ```
 * mod foo {
 *     pub struct S {
 *         field: i32
 *     }
 * }
 * fn main() {
 *     let s = S { field: 0 } // Error: the field is private. Can't instantiate `S`
 * }
 * ```
 */
fun RsFieldsOwner.canBeInstantiatedIn(mod: RsMod): Boolean =
    namedFields.all { it.isVisibleFrom(mod) } && positionalFields.all { it.isVisibleFrom(mod) }
