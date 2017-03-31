package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
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
    val element: RsCompositeElement?

    fun filterByNamespace(namespace: Namespace): ScopeEntry? {
        val element = element as? RsNamedElement ?: return null
        return if (namespace in element.namespaces.orEmpty()) this else null
    }

    companion object {
        fun of(name: String, element: RsNamedElement): ScopeEntry = SingleEntry(name, element)

        fun of(element: RsNamedElement): ScopeEntry? = element.name?.let { ScopeEntry.of(it, element) }

        fun lazy(name: String?, thunk: () -> RsCompositeElement?): ScopeEntry? =
            name?.let {
                LazyEntry(name, lazy(thunk))
            }

        fun multiLazy(name: String?, thunk: () -> List<RsCompositeElement>): ScopeEntry? =
            name?.let {
                LazyMultiEntry(name, kotlin.lazy(thunk))
            }
    }
}

private class SingleEntry(
    override val name: String,
    override val element: RsCompositeElement
) : ScopeEntry {
    override fun toString(): String = "SingleEntry($name, $element)"
}

private class LazyEntry(
    override val name: String,
    thunk: Lazy<RsCompositeElement?>
) : ScopeEntry {
    override val element: RsCompositeElement? by thunk

    override fun toString(): String = "LazyEntry($name, $element)"
}

private class LazyMultiEntry(
    override val name: String,
    thunk: Lazy<List<RsCompositeElement>>
) : ScopeEntry {
    private val elements: List<RsCompositeElement> by thunk
    override val element: RsCompositeElement? get() = elements.firstOrNull()

    override fun filterByNamespace(namespace: Namespace): ScopeEntry? =
        elements
            .filterIsInstance<RsNamedElement>()
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

val TYPES = EnumSet.of(Namespace.Types)
val VALUES = EnumSet.of(Namespace.Values)
val LIFETIMES = EnumSet.of(Namespace.Lifetimes)
val TYPES_N_VALUES = TYPES + VALUES

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

    else -> TYPES_N_VALUES
}


