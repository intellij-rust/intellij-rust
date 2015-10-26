package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlock
import org.rust.lang.core.psi.RustDeclStmt
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers
import org.rust.lang.core.resolve.scope.RustResolveScope

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
        , RustBlock
        , RustResolveScope {

    override fun listDeclarations(before: PsiElement): List<RustPatIdent> = children
            .filterIsInstance<RustDeclStmt>()
            .map { it.letDecl }
            .filterNotNull()
            .flatMap { it.pat.boundIdentifiers }
            .filter {it.textRange.endOffset < before.textRange.startOffset}
            .reversed()
}