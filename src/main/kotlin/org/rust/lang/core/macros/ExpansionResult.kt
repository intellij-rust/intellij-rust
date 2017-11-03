/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import org.rust.lang.core.psi.ext.RsCompositeElement

/**
 *  [ExpansionResult]s are those elements which exist in temporary,
 *  in-memory PSI-files and are injected into real PSI. Their real
 *  parent is this temp PSI-file, but they are seen by the rest of
 *  the plugin as the children of [getContext] element.
 */
interface ExpansionResult : RsCompositeElement {
    override fun getContext(): RsCompositeElement

    companion object {
        fun getContextImpl(psi: ExpansionResult): RsCompositeElement {
            psi.getUserData(RS_EXPANSION_CONTEXT)?.let { return it }
            (psi.parent as? RsCompositeElement)?.let { return it }
            error("Parent for ExpansionResult $psi is not RsCompositeElement")
        }
    }
}

fun ExpansionResult.setContext(context: RsCompositeElement) {
    putUserData(RS_EXPANSION_CONTEXT, context)
}


private val RS_EXPANSION_CONTEXT = Key.create<RsCompositeElement>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")

