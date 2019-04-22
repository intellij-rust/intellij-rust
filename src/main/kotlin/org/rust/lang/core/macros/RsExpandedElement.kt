/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.stubParent

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
            psi.expandedFrom?.let { return it.context }
            psi.getUserData(RS_EXPANSION_CONTEXT)?.let { return it }
            return psi.stubParent
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
    get() {
        val mgr = project.macroExpansionManager
        return when (mgr.macroExpansionMode) {
            MacroExpansionMode.Disabled -> null
            MacroExpansionMode.Old -> getUserData(RS_EXPANSION_MACRO_CALL) as? RsMacroCall
            is MacroExpansionMode.New -> mgr.getExpandedFrom(this)
        }
    }

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

