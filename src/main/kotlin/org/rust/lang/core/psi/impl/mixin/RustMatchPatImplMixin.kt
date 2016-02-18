package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustMatchPat
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundElements

abstract class RustMatchPatImplMixin(node: ASTNode)  : RustCompositeElementImpl(node)
                                                     , RustMatchPat {

    override val boundElements: Collection<RustNamedElement>
        get() = patList.flatMap { it.boundElements }
            .filterNotNull()
}

