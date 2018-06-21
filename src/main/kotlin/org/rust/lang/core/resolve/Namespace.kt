/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
import java.util.*

enum class Namespace(val itemName: String) {
    Values("value"), Types("type"), Lifetimes("lifetime"), Macros("macro")
}

val TYPES: Set<Namespace> = EnumSet.of(Namespace.Types)
val VALUES: Set<Namespace> = EnumSet.of(Namespace.Values)
val LIFETIMES: Set<Namespace> = EnumSet.of(Namespace.Lifetimes)
val MACROS: Set<Namespace> = EnumSet.of(Namespace.Macros)
val TYPES_N_VALUES: Set<Namespace> = TYPES + VALUES

val RsNamedElement.namespaces: Set<Namespace> get() = when (this) {
    is RsMod,
    is RsModDeclItem,
    is RsEnumItem,
    is RsTraitItem,
    is RsTypeParameter,
    is RsTypeAlias -> TYPES

    is RsPatBinding,
    is RsConstant,
    is RsFunction,
    is RsEnumVariant -> VALUES

    is RsStructItem -> if (blockFields == null) TYPES_N_VALUES else TYPES

    is RsLifetimeParameter -> LIFETIMES

    is RsMacroDefinition -> MACROS

    else -> TYPES_N_VALUES
}


