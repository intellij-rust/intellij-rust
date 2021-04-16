/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.ext.QueryAttributes
import org.rust.lang.core.psi.ext.isRootMetaItem
import org.rust.lang.core.psi.ext.name

enum class RsProcMacroKind {
    /**
     * Definition:
     *
     * ```
     * #[proc_macro]
     * pub fn foo(input: TokenStream) -> TokenStream {
     *     input
     * }
     * ```
     *
     * Invocation:
     *
     * ```
     * foo!();
     * ```
     */
    FUNCTION_LIKE,

    /**
     * Definition:
     *
     * ```
     * #[proc_macro_derive(Foo, attributes(helper_attr))]
     * pub fn fn_name_doesnt_matter(_item: TokenStream) -> TokenStream {
     *     "".parse().unwrap()
     * }
     * ```
     *
     * Invocation:
     *
     * ```
     * #[derive(Foo)]
     * struct Bar;
     * ```
     */
    DERIVE,

    /**
     * Definition:
     *
     * ```
     * #[proc_macro_attribute]
     * pub fn foo(_attr: TokenStream, item: TokenStream) -> TokenStream {
     *     item
     * }
     * ```
     *
     * Invocation:
     *
     * ```
     * #[foo]
     * mod bar {}
     * ```
     */
    ATTRIBUTE;

    companion object {
        fun fromDefAttributes(attrs: QueryAttributes<*>): RsProcMacroKind? {
            for (meta in attrs.metaItems) {
                when (meta.name) {
                    "proc_macro" -> return FUNCTION_LIKE
                    "proc_macro_attribute" -> return ATTRIBUTE
                    "proc_macro_derive" -> return DERIVE
                }
            }
            return null
        }

        /** Internal. Don't use it. It can return non-null value for an item that is not a proc macro call */
        fun fromMacroCall(metaItem: RsMetaItem): RsProcMacroKind? = when {
            RsPsiPattern.derivedTraitMetaItem.accepts(metaItem) -> DERIVE
            metaItem.isRootMetaItem() -> ATTRIBUTE
            else -> null
        }
    }
}
