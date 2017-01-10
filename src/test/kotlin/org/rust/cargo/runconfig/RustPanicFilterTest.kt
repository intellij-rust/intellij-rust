package org.rust.cargo.runconfig

/**
 * Tests for RustPanicFilter
 */
class RustPanicFilterTest : HighlightFilterTestBase() {

    private val filter: RustPanicFilter get() = RustPanicFilter(project, projectDir)

    fun testOneLine() =
        checkHighlights(filter,
            "thread 'main' panicked at 'something went wrong', src/main.rs:24",
            "thread 'main' panicked at 'something went wrong', [src/main.rs -> main.rs]:24")

    fun testOneLineWithLineSeparator() =
        checkHighlights(filter,
            "thread 'main' panicked at 'something went wrong', src/main.rs:24\n",
            "thread 'main' panicked at 'something went wrong', [src/main.rs -> main.rs]:24\n")

    fun testFullOuput() =
        checkHighlights(filter,
            """/Users/user/.cargo/bin/cargo run
   Compiling first_rust v0.1.0 (file:///home/user/projects/panics)
    Finished debug [unoptimized + debuginfo] target(s) in 1.20 secs
     Running `target/debug/panics`
thread 'main' panicked at 'something went wrong', src/main.rs:24""",
            "thread 'main' panicked at 'something went wrong', [src/main.rs -> main.rs]:24", 4)

}
