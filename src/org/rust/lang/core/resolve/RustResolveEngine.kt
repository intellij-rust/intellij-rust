package org.rust.lang.core.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.BaseScopeProcessor
import com.intellij.psi.scope.PsiScopeProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.scope.RustResolveScope
import java.util.*

public class RustResolveEngine(targetName: RustPathExpr) {

    private val targetName = targetName;

    private inner class ScopeProcessor : BaseScopeProcessor() {

        override fun execute(element: PsiElement, state: ResolveState): Boolean {
            if (element is RustNamedElement) {
                val name = element.getName();
                return name.equals(targetName);
            }

            return false;
        }

    }

    open class ResolveResult(elem: PsiElement?) : com.intellij.psi.ResolveResult {

        val resolvedElement = elem

        object UNRESOLVED : ResolveResult(null)

        override fun getElement(): PsiElement? {
            return resolvedElement;
        }

        override fun isValidResult(): Boolean {
            return resolvedElement != null
        }

    }

    fun runFrom(scope: RustResolveScope): ResolveResult {
        var current : RustResolveScope? = scope
        while (current != null) {
            current.lookup(targetName)?.let {
                t -> return ResolveResult(t)
            }

            current = current.getContext() as RustResolveScope?
        }

        return ResolveResult.UNRESOLVED;
    }

    private inner class ScopeVisitor : RustVisitor() {

        val matched = HashSet<RustNamedElement>()

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
                                if (match(pat)) matched.add(pat);
                            }
                        }
                    }
                }
        }

        private fun match(e: RustNamedElement): Boolean =
            e.getName().let {
                n -> when (n) {
                    null -> false
                    else -> n.equals(targetName)
                }
            }
    }

}



