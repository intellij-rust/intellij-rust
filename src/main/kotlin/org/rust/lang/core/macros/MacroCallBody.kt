/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

sealed class MacroCallBody {
    data class FunctionLike(val text: String) : MacroCallBody()
    data class Derive(val item: String) : MacroCallBody()
    data class Attribute(val item: MappedText, val attr: MappedText) : MacroCallBody()
}
