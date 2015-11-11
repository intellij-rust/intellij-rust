package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

public abstract class RustItemImplMixin(node: ASTNode)  : RustNamedElementImpl(node)
                                                        , RustItem {

    override fun getBoundElements(): Collection<RustNamedElement> =
        listOf(this)
}

