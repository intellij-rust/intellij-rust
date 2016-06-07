package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustForExprElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustForExprImplMixin(node: ASTNode)   : RustCompositeElementImpl(node)
                                                     , RustForExprElement

