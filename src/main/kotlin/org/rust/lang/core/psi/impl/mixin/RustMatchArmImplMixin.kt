package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustMatchArm
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

public abstract class RustMatchArmImplMixin(node: ASTNode)  : RustCompositeElementImpl(node)
                                                            , RustMatchArm {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        arrayListOf(matchPat)
}
