package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.util.stringLiteralValue

interface RustDocAndAttributeOwner : RustCompositeElement, NavigatablePsiElement

/**
 * Get sequence of all item's inner and outer attributes.
 * Inner attributes take precedence, so they must go first.
 */
val RustDocAndAttributeOwner.allAttributes: Sequence<RsAttr>
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
class QueryAttributes(private val attributes: Sequence<RsAttr>) {
    fun hasAttribute(attributeName: String) = metaItems.any { it.identifier.text == attributeName }

    fun hasAtomAttribute(attributeName: String): Boolean {
        val attr = attrByName(attributeName)
        return attr != null && (attr.eq == null && attr.metaItemArgs == null)
    }

    fun hasAttributeWithArg(attributeName: String, arg: String): Boolean {
        val attr = attrByName(attributeName) ?: return false
        val args = attr.metaItemArgs ?: return false
        return args.metaItemList.any { it.identifier.text == arg }
    }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.identifier.text == key }
            .mapNotNull { it.litExpr?.stringLiteralValue }
            .singleOrNull()

    val metaItems: Sequence<RsMetaItem>
        get() = attributes.mapNotNull { it.metaItem }

    private fun attrByName(name: String) = metaItems.find { it.identifier.text == name }
}
