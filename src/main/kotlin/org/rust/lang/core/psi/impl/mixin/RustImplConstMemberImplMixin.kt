package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustImplConstMemberElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil

abstract class RustImplConstMemberImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustImplConstMemberElement {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
