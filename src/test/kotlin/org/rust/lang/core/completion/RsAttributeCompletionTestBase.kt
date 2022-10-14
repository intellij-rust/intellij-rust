/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language

abstract class RsAttributeCompletionTestBase : RsCompletionTestBase() {
    protected fun doSingleAttributeCompletion(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        doSingleCompletion(before, after)
        doSingleCompletion(withCfgAttr(before), withCfgAttr(after))
    }

    protected fun checkAttributeCompletionByFileTree(
        variants: List<String>,
        @Language("Rust") code: String,
    ) {
        checkContainsCompletionByFileTree(variants, code)
        checkContainsCompletionByFileTree(variants, withCfgAttr(code))
    }
}

private fun withCfgAttr(s: String): String =
    s.replace("""(#!?)\[(.*/\*caret\*/.*)]""".toRegex(), "$1[cfg_attr(unix, $2)]")
