package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustLetDeclElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                 , RustBlockElement

