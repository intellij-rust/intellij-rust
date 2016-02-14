package org.rust.jps

import junit.framework.TestCase

class RustBuilderKtTest : TestCase() {

    fun testOneError() = doTest(
"""src/display/console.rs:15:20: 15:29 error: unresolved name `blackasdi` [E0425]
src/display/console.rs:15             black: blackasdi,
                                             ^~~~~~~~~
src/display/console.rs:15:20: 15:29 help: run `rustc --explain E0425` to see a detailed explanation""",

        listOf(
"""src/display/console.rs:15:20: 15:29 error: unresolved name `blackasdi` [E0425]
src/display/console.rs:15             black: blackasdi,
                                             ^~~~~~~~~
src/display/console.rs:15:20: 15:29 help: run `rustc --explain E0425` to see a detailed explanation"""
        )
    )

    fun testTwoErrors() = doTest(
"""src/main.rs:5:18: 5:32 error: mismatched types:
 expected `i32`,
    found `&'static str`
(expected i32,
    found &-ptr) [E0308]
src/main.rs:5     let x: i32 = "Hello, World";
                               ^~~~~~~~~~~~~~
src/main.rs:5:18: 5:32 help: run `rustc --explain E0308` to see a detailed explanation
src/main.rs:6:5: 6:8 error: expected function, found `i32`
src/main.rs:6     x();
                  ^~~
src/main.rs:5:9: 5:10 note: defined here
src/main.rs:5     let x: i32 = "Hello, World";
                      ^""",

        listOf(
"""src/main.rs:5:18: 5:32 error: mismatched types:
 expected `i32`,
    found `&'static str`
(expected i32,
    found &-ptr) [E0308]
src/main.rs:5     let x: i32 = "Hello, World";
                               ^~~~~~~~~~~~~~
src/main.rs:5:18: 5:32 help: run `rustc --explain E0308` to see a detailed explanation""",

"""src/main.rs:6:5: 6:8 error: expected function, found `i32`
src/main.rs:6     x();
                  ^~~
src/main.rs:5:9: 5:10 note: defined here
src/main.rs:5     let x: i32 = "Hello, World";
                      ^"""
        )
    )

    fun testNoErrors() = doTest("", emptyList())

    fun testIgnoresTextBeforeFirstError() = doTest(
"""Compiling hello v0.1.0 (file:///home/matklad/projects/hello)
src/main.rs:1:5: 1:12 warning: unused import, #[warn(unused_imports)] on by default
src/main.rs:1 use std::io;
                  ^~~~~~~""",

        listOf(
"""src/main.rs:1:5: 1:12 warning: unused import, #[warn(unused_imports)] on by default
src/main.rs:1 use std::io;
                  ^~~~~~~"""
        )
    )

    private fun doTest(compilerOutput: String, expectedMessages: List<String>) {
        val lines = compilerOutput.lines().asSequence()
        val messages = extractErrors(lines).map { it.text }.toList()
        assertEquals(expectedMessages, messages)
    }
}
