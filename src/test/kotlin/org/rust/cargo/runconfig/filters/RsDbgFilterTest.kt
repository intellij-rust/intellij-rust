/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

class RsDbgFilterTest : HighlightFilterTestBase() {

    private val filter: RsDbgFilter get() = RsDbgFilter(project, projectDir)

    fun `test empty dbg`() = checkHighlights(filter,
        "[src/main.rs:10]",
        "[[src/main.rs:10 -> main.rs]]"
    )

    fun `test dbg with argument`() = checkHighlights(filter,
        "[src/main.rs:3] x = 10",
        "[[src/main.rs:3 -> main.rs]] x = 10"
    )
}
