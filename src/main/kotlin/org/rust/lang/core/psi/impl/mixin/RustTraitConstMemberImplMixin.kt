package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustTraitConstMemberElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustTraitConstMemberImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                            , RustTraitConstMemberElement {
}
