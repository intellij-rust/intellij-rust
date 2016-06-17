package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustImplTypeMemberElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil

abstract class RustImplTypeMemberImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustImplTypeMemberElement {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
