package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustCondLetExpr
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustVisitor
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
        while (current != null) {
            current.resolveWith(ResolveScopeVisitor(ref))?.let {
                t -> return ResolveResult(t)
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

        override fun visitCondLetExpr(o: RustCondLetExpr) {
            visitResolveScope(o)
        }

        override fun visitResolveScope(elem: RustResolveScope) {
            elem.listDeclarations(ref)
                    .forEach { ident ->
                        if (match(ident)) {
                            return found(ident)
                        }
                    }
        }

        private fun found(elem: RustNamedElement) {
            qualifiersStack.pop();

            if (qualifiersStack.size == 0)
                matched = elem
            else when (elem) {
                is RustResolveScope -> { elem.resolveWith(this) }
                else -> { /* NOP */ }
            }
        }

        private fun match(e: RustNamedElement): Boolean =
            e.name.let { n -> qualifiersStack.peek().getReferenceNameElement().match(n) }
    }

}
