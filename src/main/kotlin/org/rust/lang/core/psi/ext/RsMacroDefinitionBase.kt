/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsMacroBody
import org.rust.stdext.HashCode

/**
 * [org.rust.lang.core.psi.RsMacro] or [org.rust.lang.core.psi.RsMacro2]
 */
interface RsMacroDefinitionBase : RsNameIdentifierOwner,
                                  RsQualifiedNamedElement,
                                  RsExpandedElement,
                                  RsModificationTrackerOwner {
    val macroBodyStubbed: RsMacroBody?
    val bodyHash: HashCode?
    val hasRustcBuiltinMacro: Boolean
}
