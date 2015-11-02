package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.impl.RustItemImpl

public abstract class RustFnItemImplMixin(node: ASTNode) : RustItemImpl(node)
                                                         , RustFnItem {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        fnParams?.paramList.orEmpty().filterNotNull()
}

