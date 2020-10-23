/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.lint

object RsRustcLintCompletionProvider : RsLintCompletionProvider() {
    override val lints: List<Lint> = RUSTC_LINTS
}
