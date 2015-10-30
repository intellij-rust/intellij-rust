package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatField
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers

public abstract class RustPatFieldImplMixin(node: ASTNode)  : RustNamedElementImpl(node)
                                                            , RustPatField {

    override fun getBoundElements(): Collection<RustNamedElement> {
        val boundInPat = pat?.boundIdentifiers
                            .orEmpty().
                            toTypedArray()

        return arrayListOf(this, *boundInPat)
    }
}