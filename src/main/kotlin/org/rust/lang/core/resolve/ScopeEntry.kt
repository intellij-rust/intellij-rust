package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import java.util.*

interface ScopeEntry {
    /**
     * The name under which an element is know in the scope.
     * It may differ from [element].name because of aliases.
     */
    val name: String

    /**
     * Potentially lazy evaluated element this entry points to.
     */
    val element: RustCompositeElement?

    fun filterByNamespace(namespace: Namespace): ScopeEntry? {
        val element = element as? RustNamedElement ?: return null
        return if (namespace in element.namespaces.orEmpty()) this else null
    }

    companion object {
        fun of(name: String, element: RustNamedElement): ScopeEntry = SingleEntry(name, element)

        fun of(element: RustNamedElement): ScopeEntry? = element.name?.let { ScopeEntry.of(it, element) }

        fun lazy(name: String?, thunk: () -> RustCompositeElement?): ScopeEntry? =
            name?.let {
                LazyEntry(name, lazy(thunk))
            }

        fun multiLazy(name: String?, thunk: () -> List<RustCompositeElement>): ScopeEntry? =
            name?.let {
                LazyMultiEntry(name, kotlin.lazy(thunk))
            }
    }
}

private class SingleEntry(
    override val name: String,
    override val element: RustCompositeElement
) : ScopeEntry {
    override fun toString(): String = "SingleEntry($name, $element)"
}

private class LazyEntry(
    override val name: String,
    thunk: Lazy<RustCompositeElement?>
) : ScopeEntry {
    override val element: RustCompositeElement? by thunk

    override fun toString(): String = "LazyEntry($name, $element)"
}

private class LazyMultiEntry(
    override val name: String,
    thunk: Lazy<List<RustCompositeElement>>
) : ScopeEntry {
    private val elements: List<RustCompositeElement> by thunk
    override val element: RustCompositeElement? get() = elements.firstOrNull()

    override fun filterByNamespace(namespace: Namespace): ScopeEntry? =
        elements
            .filterIsInstance<RustNamedElement>()
            .find { namespace in it.namespaces }?.let {
            SingleEntry(name, it)
        }

    override fun toString(): String = "LazyMultiEntry($name, $elements)"
}

enum class Namespace {
    Values, Types
}

fun Sequence<ScopeEntry>.filterByNamespace(namespace: Namespace?): Sequence<ScopeEntry> {
    if (namespace == null) return this
    return mapNotNull { it.filterByNamespace(namespace) }
}

private val TYPES = EnumSet.of(Namespace.Types)
private val VALUES = EnumSet.of(Namespace.Values)
private val BOTH = TYPES + VALUES

private val RustNamedElement.namespaces: Set<Namespace> get() = when (this) {
    is RustMod -> TYPES
    is RustEnumItemElement -> TYPES

    is RustPatBindingElement,
    is RustStaticItemElement,
    is RustFnItemElement,
    is RustEnumVariantElement,
    is RustConstItemElement -> VALUES

    is RustStructItemElement -> if (blockFields == null) BOTH else TYPES

    else -> BOTH
}


