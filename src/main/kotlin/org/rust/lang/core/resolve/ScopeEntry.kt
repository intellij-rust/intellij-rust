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

enum class Namespace(val itemName: String) {
    Values("value"), Types("type"), Lifetimes("lifetime")
}

fun Sequence<ScopeEntry>.filterByNamespace(namespace: Namespace?): Sequence<ScopeEntry> {
    if (namespace == null) return this
    return mapNotNull { it.filterByNamespace(namespace) }
}

private val TYPES = EnumSet.of(Namespace.Types)
private val VALUES = EnumSet.of(Namespace.Values)
private val LIFETIMES = EnumSet.of(Namespace.Lifetimes)
private val TYPES_N_VALUES = TYPES + VALUES

val RustNamedElement.namespaces: Set<Namespace> get() = when (this) {
    is RustMod,
    is RustEnumItemElement,
    is RustTraitItemElement,
    is RustTypeParamElement,
    is RustTypeAliasElement -> TYPES

    is RustPatBindingElement,
    is RustConstantElement,
    is RustFunctionElement,
    is RustEnumVariantElement -> VALUES

    is RustStructItemElement -> if (blockFields == null) TYPES_N_VALUES else TYPES

    is RustLifetimeParamElement -> LIFETIMES

    else -> TYPES_N_VALUES
}


