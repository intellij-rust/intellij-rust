package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustForExprElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustForExprImplMixin(node: ASTNode)   : RustCompositeElementImpl(node)
                                                     , RustForExprElement {

    override val declarations: Collection<RustDeclaringElement>
        get() = arrayListOf(scopedForDecl)
}

