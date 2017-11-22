/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

/**
 * Tests for RustPanicFilter
 */
class RsPanicFilterTest : HighlightFilterTestBase() {

    private val filter: RsPanicFilter get() = RsPanicFilter(project, projectDir)

    fun `test one line`() =
        checkHighlights(filter,
            "thread 'main' panicked at 'something went wrong', src/main.rs:24",
            "thread 'main' panicked at 'something went wrong', [src/main.rs -> main.rs]:24")

    fun `test one line with line separator`() =
        checkHighlights(filter,
            "thread 'main' panicked at 'something went wrong', src/main.rs:24\n",
            "thread 'main' panicked at 'something went wrong', [src/main.rs -> main.rs]:24\n")

    fun `test full output`() =
        checkHighlights(filter,
            """/Users/user/.cargo/bin/cargo run
   Compiling first_rust v0.1.0 (file:///home/user/projects/panics)
    Finished debug [unoptimized + debuginfo] target(s) in 1.20 secs
     Running `target/debug/panics`
thread 'main' panicked at 'something went wrong', src/main.rs:24""",
            "thread 'main' panicked at 'something went wrong', [src/main.rs -> main.rs]:24", 4)

}
