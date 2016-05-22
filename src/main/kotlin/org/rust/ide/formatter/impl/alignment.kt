package org.rust.ide.formatter.impl

import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.ARG_LIST

fun RustFmtBlock.getAlignmentStrategy(): RustAlignmentStrategy = when (node.elementType) {
    ARG_LIST -> RustAlignmentStrategy.wrap { c, p -> !c.isBlockDelim(p) }
    else -> RustAlignmentStrategy.NULL_STRATEGY
}
