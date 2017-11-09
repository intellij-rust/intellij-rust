/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.types.ty.Ty

/**
 * Psi element that defines a type and thus lives in types namespace.
 * Archetypal inheritors are structs an enums. Type aliases are type
 * declarations, while constants and statics are not. Notably, traits
 * are type declarations: a bare trait denotes a trait object type.
 *
 * Curiously, impls are also type declarations: they declare a type of
 * Self.
 */
interface RsTypeDeclarationElement : RsElement {
    val declaredType: Ty
}
