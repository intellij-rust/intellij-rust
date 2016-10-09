package org.rust.cargo.runconfig

class RustConsoleFilterTest : HighlightFilterTestBase() {

    private lateinit var filter: RustConsoleFilter

    override fun setUp() {
        super.setUp()
        filter = RustConsoleFilter(project, projectDir)
    }

    fun testTypeError() {
        val error = "src/main.rs:4:5: 4:12 error: this function takes 0 parameters but 1 parameter was supplied [E0061]\n"
        doTest(filter, error, error.length, 0, 11)
    }

    fun testOffsetsForSeveralLines() {
        val text = """/home/user/.multirust/toolchains/beta/bin/cargo run
   Compiling rustraytracer v0.1.0 (file:///home/user/projects/rustraytracer)
src/main.rs:25:26: 25:40 error: no method named `read_to_string` found for type `core::result::Result<std::fs::File, std::io::error::Error>` in the current scope"""
        val line = text.split('\n')[2]
        doTest(filter, line, text.length, 129, 140)
    }

    fun testNewErrorFormat() {
        val text = """error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]
 --> src/main.rs:4:5
 """
        val line = text.split('\n')[1]
        doTest(filter, line, text.length, 107, 118)
    }
}
