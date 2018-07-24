/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestors

/**
 *  [RsExpandedElement]s are those elements which exist in temporary,
 *  in-memory PSI-files and are injected into real PSI. Their real
 *  parent is this temp PSI-file, but they are seen by the rest of
 *  the plugin as the children of [getContext] element.
 */
interface RsExpandedElement : RsElement {
    override fun getContext(): PsiElement?

    companion object {
        fun getContextImpl(psi: RsExpandedElement): PsiElement? {
            psi.getUserData(RS_EXPANSION_CONTEXT)?.let { return it }
            if (psi is StubBasedPsiElement<*>) {
                val stub = psi.stub
                if (stub != null) return stub.parentStub.psi as RsElement
            }
            return psi.parent
        }
    }
}

fun RsExpandedElement.setContext(context: RsElement) {
    putUserData(RS_EXPANSION_CONTEXT, context)
}

fun RsExpandedElement.setExpandedFrom(call: RsMacroCall) {
    putUserData(RS_EXPANSION_MACRO_CALL, call)
}

/**
 * The [RsMacroCall] that directly expanded to this element or
 * null if this element is not directly produced by a macro
 */
val RsExpandedElement.expandedFrom: RsMacroCall?
    get() = getUserData(RS_EXPANSION_MACRO_CALL) as RsMacroCall?

val RsExpandedElement.expandedFromRecursively: RsMacroCall?
    get() {
        var call: RsMacroCall = expandedFrom ?: return null
        while (true) {
            call = call.expandedFrom ?: break
        }

        return call
    }

fun PsiElement.findMacroCallExpandedFrom(): RsMacroCall? {
    val found = ancestors
        .filterIsInstance<RsExpandedElement>()
        .mapNotNull { it.expandedFromRecursively }
        .firstOrNull()
    return found?.findMacroCallExpandedFrom() ?: found
}


private val RS_EXPANSION_CONTEXT = Key.create<RsElement>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")
private val RS_EXPANSION_MACRO_CALL = Key.create<RsElement>("org.rust.lang.core.psi.RS_EXPANSION_MACRO_CALL")

