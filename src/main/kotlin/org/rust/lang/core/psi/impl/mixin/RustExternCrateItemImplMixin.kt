package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustExternCrateReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustExternCrateItemImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustExternCrateItemElement {

    override fun getReference(): RustReference = RustExternCrateReferenceImpl(this)
}
