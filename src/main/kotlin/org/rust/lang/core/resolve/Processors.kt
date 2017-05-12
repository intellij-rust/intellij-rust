package org.rust.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.TypeArguments
import org.rust.lang.core.types.ty.emptyTypeArguments

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
    val typeArguments: TypeArguments get() = emptyTypeArguments
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

fun collectResolveVariants(referenceName: String, f: (RsResolveProcessor) -> Unit): List<BoundElement<RsCompositeElement>> {
    val result = mutableListOf<BoundElement<RsCompositeElement>>()
    f { e ->
        if (e == ScopeEvent.STAR_IMPORTS && result.isNotEmpty()) return@f true

        if (e.name == referenceName) {
            val element = e.element ?: return@f false
            result += BoundElement(element, e.typeArguments)
        }
        false
    }
    return result
}

fun collectCompletionVariants(f: (RsResolveProcessor) -> Unit): Array<LookupElement> {
    val result = mutableListOf<LookupElement>()
    f { e ->
        if ((e.element as? RsFunction?)?.isTest ?: false) return@f false
        val lookupElement = e.element?.createLookupElement(e.name)
        if (lookupElement != null) {
            result += lookupElement
        }
        false
    }
    return result.toTypedArray()
}
