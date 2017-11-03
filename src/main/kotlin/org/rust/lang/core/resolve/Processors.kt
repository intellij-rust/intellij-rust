/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.resolve.ref.MethodCallee
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.Substitution
import org.rust.lang.core.types.ty.emptySubstitution

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsCompositeElement?
    val subst: Substitution get() = emptySubstitution
}

/**
 * This special event allows to transmit "out of band" information
 * to the resolve processor
 */
enum class ScopeEvent : ScopeEntry {
    // Communicate to the resolve processor that we are about
    // to process wildecard imports. This is basically a hack
    // to make winapi 0.2 work in a reasonable amount of time.
    STAR_IMPORTS;

    override val element: RsCompositeElement? get() = null
}

/**
 * Return `true` to stop further processing,
 * return `false` to continue search
 */
typealias RsResolveProcessor = (ScopeEntry) -> Boolean
typealias RsMethodResolveProcessor = (MethodCallee) -> Boolean

fun collectResolveVariants(referenceName: String, f: (RsResolveProcessor) -> Unit): List<BoundElement<RsCompositeElement>> {
    val result = mutableListOf<BoundElement<RsCompositeElement>>()
    f { e ->
        if (e == ScopeEvent.STAR_IMPORTS && result.isNotEmpty()) return@f true

        if (e.name == referenceName) {
            val element = e.element ?: return@f false
            result += BoundElement(element, e.subst)
        }
        false
    }
    return result
}

fun collectCompletionVariants(f: (RsResolveProcessor) -> Unit): Array<LookupElement> {
    val result = mutableListOf<LookupElement>()
    f { e ->
        val element = e.element ?: return@f false
        if (element is RsFunction && element.isTest) return@f false
        result += createLookupElement(element, e.name)
        false
    }
    return result.toTypedArray()
}
