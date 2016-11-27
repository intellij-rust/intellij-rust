package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.util.stringLiteralValue
import java.util.*
import java.util.Collections.emptyIterator

interface RustDocAndAttributeOwner : RustCompositeElement, NavigatablePsiElement

/**
 * Get sequence of all item's inner and outer attributes.
 */
val RustDocAndAttributeOwner.allAttributes: Sequence<RustAttrElement>
    get() = RustAttributeIterator(this).asSequence()

/**
 * Returns [QueryAttributes] for given PSI element.
 */
val RustDocAndAttributeOwner.queryAttributes: QueryAttributes
    get() = QueryAttributes(allAttributes)

/**
 * Allows for easy querying [RustDocAndAttributeOwner] for specific attributes.
 *
 * **Do not instantiate directly**, use [RustDocAndAttributeOwner.queryAttributes] instead.
 */
class QueryAttributes(private val attributes: Sequence<RustAttrElement>) {
    fun hasAtomAttribute(name: String): Boolean =
        metaItems
            .filter { it.eq == null && it.lparen == null }
            .any { it.identifier.text == name }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.identifier.text == key }
            .mapNotNull { it.litExpr?.stringLiteralValue }
            .singleOrNull()

    val metaItems: Sequence<RustMetaItemElement>
        get() = attributes.mapNotNull { it.metaItem }
}

/**
 * Iterator that walks through both inner and outer attributes without allocating extra collections.
 * Inner attributes have priority, so they are iterated first.
 */
class RustAttributeIterator (
    val owner: RustDocAndAttributeOwner
) : Iterator<RustAttrElement> {
    var useOuter: Boolean = false
    var currentIterator: Iterator<RustAttrElement> = emptyIterator()

    init {
        if (owner is RustInnerAttributeOwner) {
            currentIterator = owner.innerAttrList.iterator()
        } else {
            switchToOuterIterator()
        }
    }

    override fun hasNext(): Boolean {
        val hasNext = currentIterator.hasNext()
        if (!hasNext && !useOuter) {
            switchToOuterIterator()
            return currentIterator.hasNext()
        }
        return hasNext
    }

    override fun next(): RustAttrElement {
        try {
            return currentIterator.next()
        } catch(e: NoSuchElementException) {
            if (!useOuter) {
                switchToOuterIterator()
                return currentIterator.next()
            }
        }
        throw NoSuchElementException()
    }

    private fun switchToOuterIterator() {
        useOuter = true
        if (owner is RustOuterAttributeOwner) {
            currentIterator = owner.outerAttrList.iterator()
        } else {
            currentIterator = emptyIterator()
        }
    }
}
