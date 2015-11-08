package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.isBefore
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
            seek(o.getDeclarations(), orderDependent = true)
        }

        override fun visitResolveScope(scope: RustResolveScope) {
            seek(scope.getDeclarations())
        }

        private fun seek(elem: RustDeclaringElement, orderDependent: Boolean = false) {
            seek(listOf(elem), orderDependent)
        }

        private fun seek(decls: Collection<RustDeclaringElement>, orderDependent: Boolean = false) {
            val boundElements = decls.flatMap { it.getBoundElements() }
            val candidates = if (orderDependent) {
                boundElements.asReversed().filter { it.isBefore(ref) }
            } else {
                boundElements
            }

            candidates.forEach { e ->
                if (match(e)) {
                    return found(e)
                }
            }
        }

        private fun found(elem: RustNamedElement) {
            matched = elem
        }

        private fun match(elem: RustNamedElement): Boolean =
            ref.getNameElement()?.let { refName ->
                elem.getNameElement()?.textMatches(refName)
            } ?: false
    }

    fun resolve(ref: RustQualifiedReferenceElement): ResolveResult {
        val scopes =
            ref.getQualifier()?.let { qual ->
                qual.reference?.let { qualRef ->
                    qualRef  .resolve()
                            ?.let { it as? RustResolveScope }
                            ?.let { listOf(it) }
                }
            } ?: enumerateScopesFor(ref)

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
