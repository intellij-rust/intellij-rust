package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import java.util.*

enum class Namespace {
    Values, Types
}

private val TYPES = EnumSet.of(Namespace.Types)
private val VALUES = EnumSet.of(Namespace.Values)
private val BOTH = TYPES + VALUES

val RustNamedElement.namespaces: Set<Namespace> get() = when (this) {
    is RustModItemElement -> TYPES

    is RustPatBindingElement,
    is RustStaticItemElement,
    is RustFnItemElement,
    is RustConstItemElement -> VALUES

    is RustStructItemElement -> if (blockFields == null && tupleFields == null) BOTH else TYPES

    else -> BOTH
}


