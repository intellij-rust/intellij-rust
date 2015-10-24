package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
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

        var matched: RustNamedElement? = null

        companion object {
            fun stackUp(ref: RustQualifiedReference): Stack<RustQualifiedReference> {
                val s = Stack<RustQualifiedReference>()

                var q: RustQualifiedReference? = ref
                while (q != null) {
                    s.add(q)
                    q = q.qualifier
                }

                return s
            }
        }

        override fun visitBlock(block: RustBlock) {
            visitDeclarationSet(block)
        }

        override fun visitFnItem(fn: RustFnItem) {
            visitDeclarationSet(fn)
        }

        private fun visitDeclarationSet(elem: RustDeclarationSet) {
            elem.listDeclarations()
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
