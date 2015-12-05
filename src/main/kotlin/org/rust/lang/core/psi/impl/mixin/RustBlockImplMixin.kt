package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                 , RustBlock {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        stmtList.filterIsInstance<RustDeclStmt>()
                .map { it.letDecl }
                .filterNotNull()
}