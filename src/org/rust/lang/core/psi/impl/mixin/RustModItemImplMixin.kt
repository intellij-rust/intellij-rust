package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.psi.items

abstract class RustModItemImplMixin(node: ASTNode) : RustItemImpl(node)
        , RustModItem {

    override fun getDeclarations(): Collection<RustDeclaringElement> {
        return items
    }
}
