package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
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

        override fun visitForExpr(o: RustForExpr) {
            seek(o.scopedForDecl)
        }

        override fun visitScopedLetExpr(o: RustScopedLetExpr) {
            visitResolveScope(o)
        }

        override fun visitLambdaExpr(o: RustLambdaExpr) {
            visitResolveScope(o)
        }

        override fun visitResolveScope(scope: RustResolveScope) {
            seek(*scope.getDeclarations().toTypedArray())
        }

        private fun seek(vararg decls: RustDeclaringElement) {
            decls   .flatMap { it.getBoundElements() }

                    /* TODO(kudinkin): We'd actually cut by the real text-offset instead of this */

                    .asReversed()
                    .forEach { e ->
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
                    qual.getReferenceNameElement().match(n) && qual.textOffset >= e.textOffset
                }
            }
    }

}
