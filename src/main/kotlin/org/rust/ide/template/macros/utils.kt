package org.rust.ide.template.macros

import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.psi.RustPatBindingElement
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.resolve.RustResolveEngine

fun getPatBindingNamesVisibleAt(pivot: RustCompositeElement?): Set<String> =
    pivot?.let {
        RustResolveEngine
            .enumerateScopesFor(pivot)
            .takeWhile {
                // we are only interested in local scopes
                if (it is RustItemElement) {
                    // workaround diamond inheritance issue
                    // (ambiguity between RustItemElement.parent & RustResolveScope.parent)
                    val item: RustItemElement = it
                    item.parent is RustBlockElement
                } else {
                    it !is RustFile
                }
            }
            .flatMap { RustResolveEngine.declarations(it, pivot) }
            .mapNotNull { (it as? RustPatBindingElement)?.name }
            .toHashSet()
    } ?: emptySet()
