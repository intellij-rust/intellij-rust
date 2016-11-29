package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.util.stringLiteralValue

interface RustDocAndAttributeOwner : RustCompositeElement, NavigatablePsiElement

/**
 * Get sequence of all item's inner and outer attributes.
 * Inner attributes take precedence, so they must go first.
 */
val RustDocAndAttributeOwner.allAttributes: Sequence<RustAttrElement>
    get() = Sequence { (this as? RustInnerAttributeOwner)?.innerAttrList.orEmpty().iterator() } +
        Sequence { (this as? RustOuterAttributeOwner)?.outerAttrList.orEmpty().iterator() }

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

    fun hasMetaItem(attribute: String, item: String): Boolean =
        metaItems
            .filter { it.identifier.text == attribute }
            .flatMap { it.metaItemList.asSequence() }
            .any { it.text == item }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.identifier.text == key }
            .mapNotNull { it.litExpr?.stringLiteralValue }
            .singleOrNull()

    val metaItems: Sequence<RustMetaItemElement>
        get() = attributes.mapNotNull { it.metaItem }
}
