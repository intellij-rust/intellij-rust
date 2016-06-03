package org.rust.lang.core.psi


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
interface RustOuterAttributeOwner : RustDocAndAttributeOwner {
    val outerAttrList: List<RustOuterAttrElement>
}

/**
 * Find the first outer attribute with the given identifier.
 */
fun RustOuterAttributeOwner.findOuterAttr(name: String): RustOuterAttrElement? =
    outerAttrList.find { it.metaItem.identifier.textMatches(name) }
