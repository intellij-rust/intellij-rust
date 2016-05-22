package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustTraitTypeMember
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustTraitTypeMemberImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                           , RustTraitTypeMember {
}
