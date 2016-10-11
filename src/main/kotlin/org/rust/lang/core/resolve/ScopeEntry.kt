package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import java.util.*

class ScopeEntry private constructor(
    val name: String,
    private val thunk: Lazy<RustNamedElement?>
) {
    val element: RustNamedElement? by thunk

    companion object {
        fun of(name: String, element: RustNamedElement): ScopeEntry = ScopeEntry(name, lazyOf(element))

        fun of(element: RustNamedElement): ScopeEntry? = element.name?.let { ScopeEntry.of(it, element) }

        fun lazy(name: String?, thunk: () -> RustNamedElement?): ScopeEntry? =
            name?.let {
                ScopeEntry(name, lazy(thunk))
            }
    }

    override fun toString(): String {
        return "ScopeEntryImpl(name='$name', thunk=$thunk)"
    }
}

enum class Namespace {
    Values, Types
}

fun Sequence<ScopeEntry>.filterByNamespace(namespace: Namespace?): Sequence<ScopeEntry> {
    if (namespace == null) return this
    return filter { namespace in it.element?.namespaces.orEmpty() }
}

private val TYPES = EnumSet.of(Namespace.Types)
private val VALUES = EnumSet.of(Namespace.Values)
private val BOTH = TYPES + VALUES

private val RustNamedElement.namespaces: Set<Namespace> get() = when (this) {
    is RustMod -> TYPES

    is RustPatBindingElement,
    is RustStaticItemElement,
    is RustFnItemElement,
    is RustEnumVariantElement,
    is RustConstItemElement -> VALUES

    is RustStructItemElement -> if (blockFields == null) BOTH else TYPES

    else -> BOTH
}


