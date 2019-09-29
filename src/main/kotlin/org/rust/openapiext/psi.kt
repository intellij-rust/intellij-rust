/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsWithMacroExpansionsRecursiveElementWalkingVisitor
import org.rust.lang.core.psi.ext.expansion


/**
 * Iterates all children of the `PsiElement` and invokes `action` for each one.
 */
inline fun PsiElement.forEachChild(action: (PsiElement) -> Unit) {
    var psiChild: PsiElement? = firstChild

    while (psiChild != null) {
        if (psiChild.node is CompositeElement) {
            action(psiChild)
        }
        psiChild = psiChild.nextSibling
    }
}

/** Behaves like [PsiTreeUtil.findChildOfAnyType], but also collects elements expanded from macros */
fun <T : PsiElement> findDescendantsWithMacrosOfAnyType(
    element: PsiElement?,
    strict: Boolean,
    vararg classes: Class<out T>
): Collection<T> {
    if (element == null) return ContainerUtil.emptyList()

    val processor = object : PsiElementProcessor.CollectElements<PsiElement>() {
        override fun execute(each: PsiElement): Boolean {
            if (strict && each === element) return true
            return if (PsiTreeUtil.instanceOf(each, *classes)) {
                super.execute(each)
            } else true
        }
    }
    processElementsWithMacros(element, processor)
    @Suppress("UNCHECKED_CAST")
    return processor.collection as Collection<T>
}

/** Behaves like [PsiTreeUtil.processElements], but also collects elements expanded from macros */
fun processElementsWithMacros(element: PsiElement, processor: PsiElementProcessor<PsiElement>): Boolean {
    if (element is PsiCompiledElement || !element.isPhysical) {
        // DummyHolders cannot be visited by walking visitors because children/parent relationship is broken there
        if (!processor.execute(element)) return false
        for (child in element.children) {
            if (child is RsMacroCall && child.macroArgument != null) {
                child.expansion?.elements?.forEach {
                    if (!processElementsWithMacros(it, processor)) return false
                }
            } else if (!processElementsWithMacros(child, processor)) {
                return false
            }
        }
        return true
    }

    var result = true
    element.accept(object : RsWithMacroExpansionsRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
            if (processor.execute(element)) {
                super.visitElement(element)
            } else {
                stopWalking()
                result = false
            }
        }
    })

    return result
}
