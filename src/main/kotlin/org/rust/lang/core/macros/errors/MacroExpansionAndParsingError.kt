/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import org.rust.lang.core.macros.MacroExpansionContext

sealed class MacroExpansionAndParsingError<out E> {
    data class ExpansionError<E>(val error: E) : MacroExpansionAndParsingError<E>()
    class ParsingError<E>(
        val expansionText: CharSequence,
        val context: MacroExpansionContext
    ) : MacroExpansionAndParsingError<E>()
    object MacroCallSyntaxError : MacroExpansionAndParsingError<Nothing>()
}
