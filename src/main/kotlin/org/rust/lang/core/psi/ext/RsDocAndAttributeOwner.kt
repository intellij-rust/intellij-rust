/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.*

interface RsDocAndAttributeOwner : RsElement, NavigatablePsiElement

interface RsInnerAttributeOwner : RsDocAndAttributeOwner {
    /**
     * Outer attributes are always children of the owning node.
     * In contrast, inner attributes can be either direct
     * children or grandchildren.
     */
    val innerAttrList: List<RsInnerAttr>
}

/**
 * An element with attached outer attributes and documentation comments.
 * Such elements should use left edge binder to properly wrap preceding comments.
 *
 * Fun fact: in Rust, documentation comments are a syntactic sugar for attribute syntax.
 *
 * ```
 * /// docs
 * fn foo() {}
 * ```
 *
 * is equivalent to
 *
 * ```
 * #[doc="docs"]
 * fn foo() {}
 * ```
 */
interface RsOuterAttributeOwner : RsDocAndAttributeOwner {
    val outerAttrList: List<RsOuterAttr>
}

/**
 * Find the first outer attribute with the given identifier.
 */
fun RsOuterAttributeOwner.findOuterAttr(name: String): RsOuterAttr? =
    outerAttrList.find { it.metaItem.referenceName == name }

/**
 * Returns [QueryAttributes] for given PSI element.
 */
val RsDocAndAttributeOwner.queryAttributes: QueryAttributes
    get() = QueryAttributes(this)

/**
 * Allows for easy querying [RsDocAndAttributeOwner] for specific attributes.
 *
 * **Do not instantiate directly**, use [RsDocAndAttributeOwner.queryAttributes] instead.
 */
class QueryAttributes(
    private val psi: RsDocAndAttributeOwner
) {
    private val attributes: Sequence<RsAttr> = Sequence { (psi as? RsInnerAttributeOwner)?.innerAttrList.orEmpty().iterator() } +
        Sequence { (psi as? RsOuterAttributeOwner)?.outerAttrList.orEmpty().iterator() }

    val isDocHidden: Boolean get() = hasAttributeWithArg("doc", "hidden")

    fun hasCfgAttr(): Boolean {
        if (psi is RsFunction) {
            val stub = psi.stub
            if (stub != null) return stub.isCfg
        }
        return hasAttribute("cfg")
    }

    // `#[attributeName]`, `#[attributeName(arg)]`, `#[attributeName = "Xxx"]`
    fun hasAttribute(attributeName: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any()
    }

    // `#[attributeName]`
    fun hasAtomAttribute(attributeName: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any { !it.hasEq && it.metaItemArgs == null }
    }

    // `#[attributeName(arg)]`
    fun hasAttributeWithArg(attributeName: String, arg: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any { it.metaItemArgs?.metaItemList?.any { it.referenceName == arg } ?: false }
    }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.referenceName == key }
            .mapNotNull { it.value }
            .singleOrNull()

    val langAttribute: String?
        get() = getStringAttributes("lang").firstOrNull()

    val deriveAttributes: Sequence<RsMetaItem>
        get() = attrsByName("derive")

    // `#[attributeName = "Xxx"]`
    private fun getStringAttributes(attributeName: String): Sequence<String?> = attrsByName(attributeName).map { it.value }

    val metaItems: Sequence<RsMetaItem>
        get() = attributes.mapNotNull { it.metaItem }

    /**
     * Get a sequence of all attributes named [name]
     */
    private fun attrsByName(name: String): Sequence<RsMetaItem> = metaItems.filter { it.referenceName == name }

    override fun toString(): String =
        "QueryAttributes(${attributes.joinToString { it.text }})"
}


