package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.RustAncestorVisitor


enum class Search {
    STOP, GO_UP
}

abstract class RustScopeProcessor() {
    abstract fun processScope(declarations: Collection<RustDeclaringElement>): Search
    open fun processBlockScope(declarations: List<RustDeclaringElement>): Search =
            processScope(declarations)
}


class RustScopeVisitor(val processor: RustScopeProcessor) : RustAncestorVisitor() {
    override fun visitModItem(o: RustModItem) {
        // resolve should not cross a module boundary, so don't visit parent
        processor.processScope(o.items)
    }

    override fun visitForExpr(o: RustForExpr) =
            visitScope(o, o.scopedForDecl)


    override fun visitScopedLetExpr(o: RustScopedLetExpr) =
            visitScope(o, o.scopedLetDecl)

    override fun visitLambdaExpr(o: RustLambdaExpr) =
            visitScope(o, o.lambdaParamList)

    override fun visitMethod(o: RustMethod) =
            visitScope(o, o.anonParams?.anonParamList.orEmpty())

    override fun visitImplMethod(o: RustImplMethod) =
            visitScope(o, o.paramList)

    override fun visitFnItem(o: RustFnItem) =
            visitScope(o, o.fnParams?.paramList.orEmpty())

    override fun visitMatchArm(o: RustMatchArm) =
            visitScope(o, o.matchPat)

    override fun visitBlock(o: RustBlock) {
        // Blocks are special, because the order of let declaration matters
        val decls = o.stmtList.filterIsInstance<RustDeclStmt>()
                .map { it.letDecl }
                .filterNotNull()

        if (processor.processBlockScope(decls) == Search.GO_UP) {
            visitParent(o)
        }
    }


    private fun visitScope(o: RustCompositeElement, singleDecl: RustDeclaringElement) =
            visitScope(o, listOf(singleDecl))


    private fun visitScope(o: RustCompositeElement,
                           decls: Collection<RustDeclaringElement>) {
        if (processor.processScope(decls) == Search.GO_UP) {
            visitParent(o)
        }
    }
}
