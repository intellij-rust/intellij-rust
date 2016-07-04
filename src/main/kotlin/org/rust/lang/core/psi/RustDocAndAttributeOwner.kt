package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.util.stringLiteralValue

interface RustDocAndAttributeOwner : RustCompositeElement, NavigatablePsiElement

/**
 * Get list of all item's inner and outer attributes.
 */
val RustDocAndAttributeOwner.allAttributes: List<RustAttrElement>
    get() = (this as? RustOuterAttributeOwner)?.outerAttrList.orEmpty() +
        (this as? RustInnerAttributeOwner)?.innerAttrList.orEmpty()

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
class QueryAttributes(private val attributes: List<RustAttrElement>) {
    fun hasAtomAttribute(name: String): Boolean =
        metaItems
            .filter { it.eq == null && it.lparen == null }
            .any { it.identifier.text == name }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.identifier.text == key }
            .mapNotNull { it.litExpr?.stringLiteralValue }
            .singleOrNull()

    val metaItems: List<RustMetaItemElement>
        get() = attributes.mapNotNull { it.metaItem }
}
