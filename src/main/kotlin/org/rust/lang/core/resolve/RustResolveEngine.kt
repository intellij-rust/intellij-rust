package org.rust.lang.core.resolve

import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.isBefore
import org.rust.lang.core.psi.util.useDeclarations
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.scope.resolveWith
import org.rust.lang.core.resolve.util.RustResolveUtil
import java.util.*

object RustResolveEngine {

    open class ResolveResult(elem: RustNamedElement?) : com.intellij.psi.ResolveResult {

        val resolved = elem

        object UNRESOLVED : ResolveResult(null)

        override fun getElement():      RustNamedElement? = resolved
        override fun isValidResult():   Boolean           = resolved != null

    }

    internal class ResolveScopeVisitor(private val name: RustNamedElement,
                                       private val visited: MutableSet<RustUseItem>) : RustVisitor() {

        var matched: RustNamedElement? = null

        override fun visitModItem(o: RustModItem) {
            seek(o.itemList)

            for (use in o.useDeclarations) {
                if (shouldStop) {
                    return
                }

                processUseDeclaration(use)
            }
        }

        override fun visitForExpr(o: RustForExpr) {
            seek(o.scopedForDecl)
        }

        override fun visitScopedLetExpr(o: RustScopedLetExpr) {
            visitResolveScope(o)
        }

        override fun visitLambdaExpr(o: RustLambdaExpr) {
            visitResolveScope(o)
        }

        override fun visitMethod(o: RustMethod) {
            visitResolveScope(o)
        }

        override fun visitFnItem(o: RustFnItem) {
            visitResolveScope(o)
        }

        override fun visitBlock(o: RustBlock) {
            o.getDeclarations()
                .takeWhile { it.isBefore(name) }
                .reversed()
                .forEach { letDecl ->
                    letDecl.getBoundElements().forEach { e ->
                        if (match(e) && !PsiTreeUtil.isAncestor(letDecl, name, true)) {
                            return found(e)
                        }
                    }
                }
        }

        override fun visitResolveScope(scope: RustResolveScope) {
            seek(scope.getDeclarations())
        }

        private fun processUseDeclaration(use: RustUseItem) {
            val path = use.viewPath
            val pathPart = path.pathPart ?: return

            val isPlainPathImport = path.mul == null && path.lbrace == null
            if (isPlainPathImport) {
                val name = path.alias ?: pathPart
                if (match(name)) {
                    if (use in visited) {
                        return
                    }
                    visited += use

                    val item = RustResolveEngine.resolve(pathPart, visited).element  ?: return
                    return found(item)
                }
            }
        }


        private fun seek(elem: RustDeclaringElement) {
            seek(listOf(elem))
        }

        private fun seek(decls: Collection<RustDeclaringElement>) {
            decls.flatMap { it.getBoundElements() }
                 .forEach { e ->
                     if (match(e)) {
                         return found(e)
                     }
                 }
        }

        private fun found(elem: RustNamedElement) {
            matched = elem

            if (elem is RustModDeclItem) {
                elem.reference?.resolve().let {
                    matched = it
                }
            }
        }

        private fun match(elem: RustNamedElement): Boolean =
            name.nameElement?.let { refName ->
                elem.nameElement?.textMatches(refName)
            } ?: false

        private val shouldStop: Boolean
            get() = matched != null
    }

    fun resolve(ref: RustQualifiedReferenceElement): ResolveResult {
        return resolve(ref, HashSet())
    }

    private fun resolve(ref: RustQualifiedReferenceElement, visited: MutableSet<RustUseItem>): ResolveResult {
        val qual = ref.qualifier

        if (qual != null) {
            val parent = resolve(qual, visited).element
            return when (parent) {
                is RustResolveScope -> resolveIn(ResolveScopeVisitor(ref, visited), listOf(parent))
                else                -> ResolveResult.UNRESOLVED
            }
        }
        return resolveIn(ResolveScopeVisitor(ref, visited), enumerateScopesFor(ref))
    }

    private fun resolveIn(v: ResolveScopeVisitor, scopes: Iterable<RustResolveScope>): ResolveResult {
        scopes.forEach { s ->
            s.resolveWith(v)?.let {
                return ResolveResult(it)
            }
        }

        return ResolveResult.UNRESOLVED
    }


    private fun enumerateScopesFor(ref: RustQualifiedReferenceElement): Iterable<RustResolveScope> {
        if (ref.isFullyQualified) {
            return listOfNotNull(RustResolveUtil.getCrateRootFor(ref))
        }

        return object: Iterable<RustResolveScope> {
            override fun iterator(): Iterator<RustResolveScope> {
                return object: Iterator<RustResolveScope> {

                    var next = RustResolveUtil.getResolveScopeFor(ref)

                    fun climb(): RustResolveScope {
                        val prev = next!!
                        next =  when (prev) {
                                    is RustModItem -> null
                                    else           -> RustResolveUtil.getResolveScopeFor(prev)
                                }
                        return prev
                    }

                    override fun next(): RustResolveScope = climb()

                    override fun hasNext(): Boolean = next != null
                }
            }
        }
    }
}
