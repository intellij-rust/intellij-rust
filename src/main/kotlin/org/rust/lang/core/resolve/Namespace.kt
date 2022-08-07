/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.IS_PROC_MACRO_DEF_PROP
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.isProcMacroDef
import org.rust.lang.core.stubs.*
import java.util.*

enum class Namespace(val itemName: String) {
    Types("type"),
    Values("value"),
    Lifetimes("lifetime"),
    Macros("macro")
}

val TYPES: Set<Namespace> = EnumSet.of(Namespace.Types)
val VALUES: Set<Namespace> = EnumSet.of(Namespace.Values)
val LIFETIMES: Set<Namespace> = EnumSet.of(Namespace.Lifetimes)
val MACROS: Set<Namespace> = EnumSet.of(Namespace.Macros)
val TYPES_N_VALUES: Set<Namespace> = EnumSet.of(Namespace.Types, Namespace.Values)
val TYPES_N_VALUES_N_MACROS: Set<Namespace> = EnumSet.of(Namespace.Types, Namespace.Values, Namespace.Macros)

val RsNamedElement.namespaces: Set<Namespace> get() = when (this) {
    is RsMod,
    is RsModDeclItem,
    is RsEnumItem,
    is RsTraitItem,
    is RsTypeParameter,
    is RsTypeAlias -> TYPES

    is RsPatBinding,
    is RsConstParameter,
    is RsConstant -> VALUES
    is RsFunction -> if (this.isProcMacroDef) MACROS else VALUES

    is RsEnumVariant -> ENUM_VARIANT_NS

    is RsStructItem -> if (blockFields == null) TYPES_N_VALUES else TYPES

    is RsLifetimeParameter -> LIFETIMES

    is RsMacro, is RsMacro2 -> MACROS

    else -> TYPES_N_VALUES
}

fun StubElement<*>.getNamespaces(crate: Crate): Set<Namespace> = when (this) {
    is RsModItemStub,
    is RsModDeclItemStub,
    is RsEnumItemStub,
    is RsTraitItemStub,
    is RsTypeParameterStub,
    is RsTypeAliasStub -> TYPES

    is RsConstantStub -> VALUES
    is RsFunctionStub -> if (IS_PROC_MACRO_DEF_PROP.getByStub(this, crate)) MACROS else VALUES

    is RsEnumVariantStub -> ENUM_VARIANT_NS

    is RsStructItemStub -> if (blockFields == null) TYPES_N_VALUES else TYPES

    is RsLifetimeParameterStub -> LIFETIMES

    is RsMacroStub, is RsMacro2Stub -> MACROS

    else -> TYPES_N_VALUES
}

// https://rust-lang.github.io/rfcs/0234-variants-namespace.html
val ENUM_VARIANT_NS: Set<Namespace> = TYPES_N_VALUES

val RsUseSpeck.namespaces: Set<Namespace>
    get() = path
        ?.reference
        ?.multiResolveIfVisible()
        ?.asSequence()
        ?.filterIsInstance<RsNamedElement>()
        ?.flatMap { it.namespaces.asSequence() }
        ?.toSet()
        .orEmpty()

