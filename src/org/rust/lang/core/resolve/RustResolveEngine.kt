package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.isBefore
import org.rust.lang.core.psi.util.match
import org.rust.lang.core.resolve.ref.RustQualifiedValue
import java.util.*

public class RustResolveEngine(private val ref: RustQualifiedValue) {

    open class ResolveResult(elem: RustNamedElement?) : com.intellij.psi.ResolveResult {

        val resolved = elem

        object UNRESOLVED : ResolveResult(null)

        override fun getElement(): PsiElement? = resolved
        override fun isValidResult(): Boolean = resolved != null

    }

    private class ResolveScopeProcessor(val ref: RustQualifiedValue) : RustScopeProcessor() {

        val qualifiersStack = stackUp(ref)

        var matched: RustNamedElement? = null

        var goesUp = true

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

        override fun processScope(declarations: Collection<RustDeclaringElement>): Search =
                searchIn(declarations.flatMap { it.getBoundElements() })


        override fun processBlockScope(declarations: List<RustDeclaringElement>): Search =
                searchIn(declarations
                        .asReversed()
                        .flatMap { it.getBoundElements() }
                        .filter { it.isBefore(ref) })

        private fun searchIn(names: Collection<RustNamedElement>): Search {
            names.forEach {
                if (match(it)) {
                    found(it)
                    return Search.STOP
                }
            }
            return if (goesUp) Search.GO_UP else Search.STOP
        }

        private fun found(elem: RustNamedElement) {
            goesUp = false
            qualifiersStack.pop()

            if (qualifiersStack.size == 0)
                matched = elem
            else {
                elem.accept(RustScopeVisitor(this))
            }
        }

        private fun match(e: RustNamedElement): Boolean =
                e.name.let { n ->
                    qualifiersStack.peek().let { qual ->
                        qual.getReferenceNameElement().match(n)
                    }
                }
    }

    fun runFrom(element: PsiElement?): ResolveResult {
        val processor = ResolveScopeProcessor(ref)
        element?.accept(RustScopeVisitor(processor))
        return if (processor.matched == null) {
            ResolveResult.UNRESOLVED
        } else {
            ResolveResult(processor.matched)
        }
    }
}
