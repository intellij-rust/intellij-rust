package org.rust.cargo.runconfig

/**
 * Tests for RustPanicFilter
 */
class RustPanicFilterTest : HighlightFilterTestBase() {

    private lateinit var filter: RustPanicFilter

    override fun setUp() {
        super.setUp()
        filter = RustPanicFilter(project, projectDir)
    }

    fun testOneLine() {
        val text = "thread 'main' panicked at 'something went wrong', src/main.rs:24"
        doTest(filter, text, text.length, 50, 61)
    }

    fun testFullOuput() {
        val output = """/Users/user/.cargo/bin/cargo run
   Compiling first_rust v0.1.0 (file:///home/user/projects/panics)
    Finished debug [unoptimized + debuginfo] target(s) in 1.20 secs
     Running `target/debug/panics`
thread 'main' panicked at 'something went wrong', src/main.rs:24"""
        val line = output.split('\n')[4]
        doTest(filter, line, output.length, 253, 264)
    }

}
