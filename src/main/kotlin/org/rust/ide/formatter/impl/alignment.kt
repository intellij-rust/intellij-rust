package org.rust.ide.formatter.impl

import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*

fun RustFmtBlock.getAlignmentStrategy(): RustAlignmentStrategy = when (node.elementType) {
    TUPLE_EXPR, TUPLE_TYPE, ARG_LIST, ENUM_TUPLE_ARGS ->
        RustAlignmentStrategy.wrap()
            .cond { c, p, x -> !c.isBlockDelim(p) }

    in PARAMS_LIKE ->
        RustAlignmentStrategy.shared()
            .cond { c, p, x -> !c.isBlockDelim(p) }

    in FN_DECLS ->
        RustAlignmentStrategy.shared()
            .filter(RET_TYPE, WHERE_CLAUSE)

    PAT_ENUM ->
        RustAlignmentStrategy.wrap()
            .cond { c, p, x -> x.metLBrace && !c.isBlockDelim(p) }

    else -> RustAlignmentStrategy.NullStrategy
}
