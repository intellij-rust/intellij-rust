package org.rust.lang.core.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.rust.lang.core.psi.RustNamedElement

public class RustResolveProcessor(targetName: String) : PsiScopeProcessor {

    val TargetName = targetName;

    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}

    override fun <T> getHint(hintKey: Key<T>): T {
        throw UnsupportedOperationException()
    }

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        if (element is RustNamedElement) {
            val name = element.getName();
            return name != null && name.equals(TargetName);
        }

        return false;
    }

}