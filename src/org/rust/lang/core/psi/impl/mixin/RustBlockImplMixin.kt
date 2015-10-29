package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundVariables
import org.rust.lang.core.resolve.scope.RustResolveScope

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
        , RustBlock
        , RustResolveScope {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = stmtList
            .filterIsInstance<RustDeclStmt>()
            .map { it.letDecl }
            .filterNotNull()
            .flatMap { it.pat.boundVariables }
            .filter {it.textRange.endOffset < before.textRange.startOffset}
            .reversed()
}