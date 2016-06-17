package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustTupleFieldDeclElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil

abstract class RustTupleFieldDeclImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustTupleFieldDeclElement {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
