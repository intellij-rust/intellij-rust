/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

class RsConsoleFilterTest : HighlightFilterTestBase() {

    private val filter: RsConsoleFilter get() = RsConsoleFilter(project, projectDir)

    fun `test type error`() =
        checkHighlights(filter,
            "src/main.rs:4:5: 4:12 error: this function takes 0 parameters but 1 parameter was supplied [E0061]",
            "[src/main.rs -> main.rs]:4:5: 4:12 error: this function takes 0 parameters but 1 parameter was supplied [E0061]")

    fun `test offsets for several lines`() =
        checkHighlights(filter,
            """/home/user/.multirust/toolchains/beta/bin/cargo run
   Compiling rustraytracer v0.1.0 (file:///home/user/projects/rustraytracer)
src/main.rs:25:26: 25:40 error: no method named `read_to_string` found for type `core::result::Result<std::fs::File, std::io::error::Error>` in the current scope""",
            "[src/main.rs -> main.rs]:25:26: 25:40 error: no method named `read_to_string` found for type `core::result::Result<std::fs::File, std::io::error::Error>` in the current scope", 2)

    fun `test new error format`() =
        checkHighlights(filter,
            """error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]
 --> src/main.rs:4:5""",
            " --> [src/main.rs -> main.rs]:4:5", 1)

    fun `test new error format more spaces`() =
        checkHighlights(filter,
            """error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]
   --> src/main.rs:4:5""",
            "   --> [src/main.rs -> main.rs]:4:5", 1)

    fun `test new error format new line`() =
        checkHighlights(filter,
            """error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]
 --> src/main.rs:4:5
""",
            " --> [src/main.rs -> main.rs]:4:5\n", 1)

}
