package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustForeignFnDeclElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil

abstract class RustForeignFnDeclImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustForeignFnDeclElement {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
