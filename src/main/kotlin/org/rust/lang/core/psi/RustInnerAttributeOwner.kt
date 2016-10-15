package org.rust.lang.core.psi

interface RustInnerAttributeOwner : RustDocAndAttributeOwner {
    /**
     * Outer attributes are always children of the owning node.
     * In contrast, inner attributes can be either direct
     * children or grandchildren.
     */
    val innerAttrList: List<RustInnerAttrElement>
}

/**
 * Find the first inner attribute with the given identifier.
 */
fun RustInnerAttributeOwner.findInnerAttr(name: String): RustInnerAttrElement? =
    innerAttrList.find { it.metaItem.identifier.textMatches(name) }
