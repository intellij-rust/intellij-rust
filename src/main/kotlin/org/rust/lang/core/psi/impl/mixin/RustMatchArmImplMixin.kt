package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustMatchArmElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustMatchArmImplMixin(node: ASTNode)  : RustCompositeElementImpl(node)
                                                     , RustMatchArmElement {

}
