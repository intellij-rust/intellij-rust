package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.impl.RustItemImpl


import org.rust.lang.core.resolve.ref.RustModReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustModDeclItemImplMixin(node: ASTNode) : RustItemImpl(node)
                                                       , RustModDeclItem {

    override fun getReference(): RustReference = RustModReferenceImpl(this)
}
