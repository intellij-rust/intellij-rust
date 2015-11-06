package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.isBefore
import org.rust.lang.core.psi.util.match
import org.rust.lang.core.resolve.ref.RustQualifiedValue
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.scope.resolveWith
import org.rust.lang.core.resolve.util.RustResolveUtil
import java.util.*

public class RustResolveEngine(ref: RustQualifiedValue) {

    private val ref = ref;

    open class ResolveResult(elem: RustNamedElement?) : com.intellij.psi.ResolveResult {

        val resolved = elem

        object UNRESOLVED : ResolveResult(null)

        override fun getElement():      PsiElement? = resolved
        override fun isValidResult():   Boolean     = resolved != null

    }

    fun runFrom(scope: RustResolveScope?): ResolveResult {
        var current = scope
        val visitor = ResolveScopeVisitor(ref)
        while (current != null) {
            current.resolveWith(visitor)?.let {
                return ResolveResult(it)
            }

            current = RustResolveUtil.getResolveScope(current)
        }

        return ResolveResult.UNRESOLVED;
    }

    internal class ResolveScopeVisitor(val ref: RustQualifiedValue) : RustVisitor() {

        val qualifiersStack = stackUp(ref)

        var matched: RustNamedElement? = null

        companion object {
            fun stackUp(ref: RustQualifiedValue): Stack<RustQualifiedValue> {
                val s = Stack<RustQualifiedValue>()

                var q: RustQualifiedValue? = ref
                while (q != null) {
                    s.add(q)
                    q = q.getQualifier()
                }

                return s
            }
        }

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
            qualifiersStack.pop();

            if (qualifiersStack.size == 0)
                matched = elem
            else if (elem is RustResolveScope) {
                elem.resolveWith(this)
            }
        }

        private fun match(e: RustNamedElement): Boolean =
            e.name.let { n ->
                qualifiersStack.peek().let { qual ->
                    qual.getReferenceNameElement().match(n)
                }
            }
    }

}
