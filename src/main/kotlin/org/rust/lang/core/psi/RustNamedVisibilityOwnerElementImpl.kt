package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustNamedVisibilityOwnerElementImpl(node: ASTNode) : RustNamedElementImpl(node), RustVisibilityOwner {
    override val isPublic: Boolean get() = vis != null
}
