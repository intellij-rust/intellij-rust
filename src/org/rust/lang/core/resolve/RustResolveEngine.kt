package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.isBefore
import org.rust.lang.core.psi.util.moduleFile
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.scope.resolveWith
import org.rust.lang.core.resolve.util.RustResolveUtil

public class RustResolveEngine() {

    open class ResolveResult(elem: RustNamedElement?) : com.intellij.psi.ResolveResult {

        val resolved = elem

        object UNRESOLVED : ResolveResult(null)

        override fun getElement():      PsiElement? = resolved
        override fun isValidResult():   Boolean     = resolved != null

    }

    //
    // TODO(kudinkin): Replace with just 'name' instance
    //
    internal class ResolveScopeVisitor(private val ref: RustQualifiedReferenceElement) : RustVisitor() {

        var matched: RustNamedElement? = null

        override fun visitModItem(o: RustModItem) {
            seek(o.itemList)
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
                .takeWhile { it.isBefore(ref) }
                .reversed()
                .forEach { letDecl ->
                    letDecl.getBoundElements().forEach { e ->
                        if (match(e) && !PsiTreeUtil.isAncestor(letDecl, ref, true)) {
                            return found(e)
                        }
                    }
                }
        }

        override fun visitResolveScope(scope: RustResolveScope) {
            seek(scope.getDeclarations())
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
                val submod = elem.moduleFile.mod
                if (submod != null) {
                    matched = submod
                }
            }

        }

        private fun match(elem: RustNamedElement): Boolean =
            ref.getNameElement()?.let { refName ->
                elem.getNameElement()?.textMatches(refName)
            } ?: false
    }

    fun resolve(ref: RustQualifiedReferenceElement): ResolveResult {
        val qual = ref.getQualifier()

        val scopes = if (qual != null) {
            val parent = qual.reference.resolve()
            when (parent) {
                is RustResolveScope -> listOf(parent)
                else -> return ResolveResult.UNRESOLVED
            }
        } else {
            enumerateScopesFor(ref)
        }

        return resolveIn(ResolveScopeVisitor(ref), scopes)
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
        if (ref.isFullyQualified)
            return listOf(RustResolveUtil.getGlobalResolveScopeFor(ref))

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
