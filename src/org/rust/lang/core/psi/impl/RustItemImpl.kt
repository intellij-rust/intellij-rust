package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.*

public abstract class RustItemImpl(node: ASTNode)   : RustNamedElementImpl(node)
                                                    , RustItem {

    override val attrs: List<RustOuterAttr>?
        get() = findChildrenByType(RustCompositeElementTypes.OUTER_ATTR)

    override val vis: RustVis?
        get() = findChildByType(RustCompositeElementTypes.VIS)

    override fun getBoundElements(): Collection<RustNamedElement> = listOf(this)
}

