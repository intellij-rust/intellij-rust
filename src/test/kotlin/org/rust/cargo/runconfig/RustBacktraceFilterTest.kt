package org.rust.cargo.runconfig

/**
 * Tests for RustBacktraceFilter
 */
class RustBacktraceFilterTest : HighlightFilterTestBase() {

    private lateinit var filter: RustBacktraceFilter

    override fun setUp() {
        super.setUp()
        filter = RustBacktraceFilter(project, projectDir)
    }

    fun testOneLine() {
        val text = "          at src/main.rs:24"
        doTest(filter, text, text.length, 13, 24)
    }

    fun testAbsolutePath() {
        val absPath = "${projectDir.canonicalPath}/src/main.rs"
        val text = "          at $absPath:24"
        doTest(filter, text, text.length, 13, 47)
    }

    fun testFullOutput() {
        val output = """    Running `target/debug/test`
thread '<main>' panicked at 'called `Option::unwrap()` on a `None` value', ../src/libcore/option.rs:325
stack backtrace:
   1:     0x7feeefb45b1f - std::sys::backtrace::tracing::imp::write::h3800f45f421043b8
   2:     0x7feeefb47b93 - std::panicking::default_hook::hf3839060ccbb8764
   3:     0x7feeefb4095d - std::panicking::rust_panic_with_hook::h5dd7da6bb3d06020
   4:     0x7feeefb48151 - std::panicking::begin_panic::h9bf160aee246b9f6
   5:     0x7feeefb411fa - std::panicking::begin_panic_fmt::haf08a9a70a097ee1
   6:     0x7feeefb480ee - rust_begin_unwind
   7:     0x7feeefb7d11f - core::panicking::panic_fmt::h93df64e7370b5253
   8:     0x7feeefb7d3f8 - core::panicking::panic::h9d5bd65bbb401959
   9:     0x7feeefb3f765 - _<std..option..Option<T>>::unwrap::hbe9ea065746f6376
                        at ../src/libcore/macros.rs:21
  10:     0x7feeefb3f538 - btest::main::h888e623968051ab6
                        at src/main.rs:22"""
        val line = output.split('\n')[14]
        doTest(filter, line, output.length, 957, 968)
    }

}
