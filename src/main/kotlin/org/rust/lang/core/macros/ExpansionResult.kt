/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.psi.StubBasedPsiElement
import org.rust.lang.core.psi.ext.RsElement

/**
 *  [ExpansionResult]s are those elements which exist in temporary,
 *  in-memory PSI-files and are injected into real PSI. Their real
 *  parent is this temp PSI-file, but they are seen by the rest of
 *  the plugin as the children of [getContext] element.
 */
interface ExpansionResult : RsElement {
    override fun getContext(): RsElement

    companion object {
        fun getContextImpl(psi: ExpansionResult): RsElement {
            psi.getUserData(RS_EXPANSION_CONTEXT)?.let { return it }
            if (psi is StubBasedPsiElement<*>) {
                val stub = psi.stub
                if (stub != null) return stub.parentStub.psi as RsElement
            }
            (psi.parent as? RsElement)?.let { return it }
            error("Parent for ExpansionResult $psi is not RsElement")
        }
    }
}

fun ExpansionResult.setContext(context: RsElement) {
    putUserData(RS_EXPANSION_CONTEXT, context)
}


private val RS_EXPANSION_CONTEXT = Key.create<RsElement>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")

