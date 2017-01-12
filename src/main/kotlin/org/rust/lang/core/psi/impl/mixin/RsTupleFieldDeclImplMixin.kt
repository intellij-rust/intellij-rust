package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.impl.RsCompositeElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil

abstract class RsTupleFieldDeclImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsTupleFieldDecl {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
