/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language

abstract class RsAttributeCompletionTestBase : RsCompletionTestBase() {
    protected fun doSingleAttributeCompletion(@Language("Rust") before: String, @Language("Rust") after: String) {
        fun String.withCfgAttr(): String = replace("""(#!?)\[(.*/\*caret\*/.*)]""".toRegex(), "$1[cfg_attr(unix, $2)]")

        doSingleCompletion(before, after)
        doSingleCompletion(before.withCfgAttr(), after.withCfgAttr())
    }
}
