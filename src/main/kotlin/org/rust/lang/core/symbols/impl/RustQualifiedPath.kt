package org.rust.lang.core.symbols.impl

import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.symbols.RustQualifiedPathPart
import org.rust.lang.core.symbols.unfold
import org.rust.utils.int

internal class RustQualifiedPath(
    override val qualifier: RustQualifiedPath?,
    override val part: RustQualifiedPathPart,
    override val fullyQualified: Boolean
) : RustQualifiedPath {

    init {
        // FQ-path may not have qualifier
        check(!fullyQualified || qualifier == null)
    }


    override fun equals(other: Any?): Boolean {
        if (other !is org.rust.lang.core.symbols.impl.RustQualifiedPath)
            return false

        val lop = unfold().toList()
        val rop = other.unfold().toList()

        return lop == rop
    }

    override fun hashCode(): Int =
        (qualifier?.hashCode() ?: 0) * 11437 + part.hashCode() + fullyQualified.int * 4129

    override fun toString(): String {
        return if (qualifier != null)
            "$qualifier::${part.name}"
        else if (fullyQualified)
            "::${part.name}"
        else
            "${part.name}"
    }
}
