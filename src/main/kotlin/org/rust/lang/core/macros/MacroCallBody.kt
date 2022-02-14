/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.RsProcMacroKind

sealed class MacroCallBody {
    data class FunctionLike(val text: String) : MacroCallBody()
    data class Derive(val item: String) : MacroCallBody()

    /**
     * An attribute procedural macro body consists of two parts: an [item] part and an [attribute][attr] part.
     *
     * ```rust
     * #[foo(bar)]
     * fn baz() {}
     * ```
     *
     * In this example, `bar` is an [attr] part and `fn baz() {}` is an [item] part.
     *
     * Attribute procedural macro declaration accepts [attr] and [item] as parameters:
     *
     * ```rust
     * #[proc_macro_attribute]
     * pub fn foo(attr: TokenStream, item: TokenStream) -> TokenStream {
     *     attr
     * }
     * ```
     */
    data class Attribute(val item: MappedText, val attr: MappedText) : MacroCallBody()

    val kind: RsProcMacroKind
        get() = when (this) {
            is Attribute -> RsProcMacroKind.ATTRIBUTE
            is Derive -> RsProcMacroKind.DERIVE
            is FunctionLike -> RsProcMacroKind.FUNCTION_LIKE
        }
}
