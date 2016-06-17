package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil

abstract class RustUseItemImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustUseItemElement {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
