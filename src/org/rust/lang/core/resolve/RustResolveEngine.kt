package org.rust.lang.core.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.BaseScopeProcessor
import com.intellij.psi.scope.PsiScopeProcessor
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPathExpr
import org.rust.lang.core.resolve.scope.RustResolveScope

public class RustResolveEngine(targetName: RustPathExpr) {

    private val target = targetName;

    private inner class ScopeProcessor : BaseScopeProcessor() {

        override fun execute(element: PsiElement, state: ResolveState): Boolean {
            if (element is RustNamedElement) {
                val name = element.getName();
                return name.equals(target);
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
            val target = current.lookup(target)
            if (target != null) {
                return ResolveResult(target)
            }

            current = current.getContext() as RustResolveScope?
        }

        return ResolveResult.UNRESOLVED;
    }

}



