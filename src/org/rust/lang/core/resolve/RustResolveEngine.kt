package org.rust.lang.core.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.BaseScopeProcessor
import com.intellij.psi.scope.PsiScopeProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.match
import org.rust.lang.core.resolve.ref.RustQualifiedReference
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.scope.resolveWith
import org.rust.lang.core.resolve.util.RustResolveUtil
import java.util.*

public class RustResolveEngine(ref: RustQualifiedReference) {

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

    internal class ResolveScopeVisitor(ref: RustQualifiedReference) : RustVisitor() {

        val qualifiersStack = stackUp(ref)

        var matched : RustNamedElement? = null

        companion object {
            fun stackUp(ref: RustQualifiedReference): Stack<RustQualifiedReference> {
                val s = Stack<RustQualifiedReference>()

                var q : RustQualifiedReference? = ref
                while (q != null) {
                    s.add(q)
                    q = q.getQualifier()
                }

                return s
            }
        }

        override fun visitFnItem(fn: RustFnItem) {
            // Lookup only after parameter-names, since
            // block-level scope should be visited already
            fn.getFnParams()?.let {
                params -> params.getParamList()
                    .map        { p -> p.getPat() }
                    .forEach    {
                        pat -> run {
                            // NB: It's purposefully incomplete
                            if (pat is RustPatIdent)
                            {
                                if (match(pat)) found(pat)
                            }
                        }
                    }
                }
        }

        private fun found(elem: RustNamedElement) {
            qualifiersStack.pop();

            if (qualifiersStack.size() == 0)
                matched = elem
            else when (elem) {
                is RustResolveScope -> { elem.resolveWith(this) }
                else -> { /* NOP */ }
            }
        }

        private fun match(e: RustNamedElement): Boolean =
            e.getName().let { n -> qualifiersStack.peek().getReferenceNameElement().match(n) }
    }

}
