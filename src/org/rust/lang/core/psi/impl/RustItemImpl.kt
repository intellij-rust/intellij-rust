package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.RustOuterAttr
import org.rust.lang.core.psi.RustVis

public abstract class RustItemImpl(node: ASTNode)   : RustNamedElementImpl(node)
                                                    , RustItem {

    override fun getAttrs(): List<RustOuterAttr>? =
        findChildrenByType(RustCompositeElementTypes.OUTER_ATTR)

    override fun getVis(): RustVis? =
        findChildByType(RustCompositeElementTypes.VIS)
}

