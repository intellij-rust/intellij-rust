/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class RsConsoleFoldingTest : RsConsoleFoldingTestBase() {
    fun `test do not fold unrelated text`() = doFoldingTest("""
        //- lib.rs
        fn foo() {}
    """, """
        Hello world
        1 + 1 = 2
    """)

    fun `test single line`() = doFoldingTest("""
        //- lib.rs
        fn foo() {}
    """, """
        0: backtrace::backtrace::libunwind::trace
    """)

    fun `test single call`() = doFoldingTest("""
        //- lib.rs
        fn foo() {}
    """, """
        //foldstart <1 internal call>
        0: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        //foldend
        1: test_package::foo
            at lib.rs:1
    """)

    fun `test multiple calls`() = doFoldingTest("""
        //- lib.rs
        fn foo() {}
    """, """
        //foldstart <2 internal calls>
        0: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        1: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        //foldend
        2: test_package::foo
            at lib.rs:1
        3: test_package::foo
            at lib.rs:1
    """)

    fun `test before and after`() = doFoldingTest("""
        //- lib.rs
        fn foo() {}
    """, """
        //foldstart <2 internal calls>
        0: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        1: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        //foldend
        2: test_package::foo
            at lib.rs:1
        3: test_package::foo
            at lib.rs:1
        //foldstart <2 internal calls>
        4: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        5: backtrace::backtrace::libunwind::trace
            at /cargo/registry/src/github.com-1ecc6299db9ec823/backtrace-0.3.46/src/backtrace/libunwind.rs:86
        //foldend
    """)

    fun `test non-existent code`() = doFoldingTest("""
        //- lib.rs
        fn foo() {}
    """, """
        //foldstart <1 internal call>
        0: foo::bar
            at foo/bar.rs:1
        //foldend
        1: test_package::foo
            at lib.rs:1
    """)
}
