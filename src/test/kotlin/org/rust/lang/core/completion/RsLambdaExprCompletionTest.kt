/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsLambdaExprCompletionTest : RsCompletionTestBase() {
    fun `test complete after pipe`() = doTest("""
        fn foo(f: fn(u32) -> ()) {}

        fn main() {
            foo(|/*caret*/);
        }
    """, """
        fn foo(f: fn(u32) -> ()) {}

        fn main() {
            foo(|i| {/*caret*/});
        }
    """)

    fun `test no arguments`() = doTest("""
        fn foo(f: fn() -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo(f: fn() -> ()) {}

        fn main() {
            foo(|| {/*caret*/});
        }
    """)

    fun `test multiple arguments`() = doTest("""
        fn foo(f: fn(u32, bool) -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo(f: fn(u32, bool) -> ()) {}

        fn main() {
            foo(|i/*caret*/, x| {});
        }
    """)

    fun `test multiple arguments of same type`() = doTest("""
        struct S;
        fn foo(f: fn(u32, u32, S, bool, bool, S, S) -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        struct S;
        fn foo(f: fn(u32, u32, S, bool, bool, S, S) -> ()) {}

        fn main() {
            foo(|i/*caret*/, i1, s, x, x1, s1, s2| {});
        }
    """)

    fun `test custom struct`() = doTest("""
        struct MyStruct;
        fn foo(f: fn(u32, MyStruct) -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        struct MyStruct;
        fn foo(f: fn(u32, MyStruct) -> ()) {}

        fn main() {
            foo(|i/*caret*/, my_struct| {});
        }
    """)

    fun `test template`() = doTestWithTemplate("""
        fn foo(f: fn(u32, bool) -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, "foo\tbar\t1 + 2\t", """
        fn foo(f: fn(u32, bool) -> ()) {}

        fn main() {
            foo(|foo, bar| 1 + 2/*caret*/);
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test impl Fn`() = doTest("""
        fn foo(f: impl Fn(u32, bool) -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo(f: impl Fn(u32, bool) -> ()) {}

        fn main() {
            foo(|i/*caret*/, x| {});
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dyn Fn`() = doTest("""
        fn foo(f: &dyn Fn(u32, bool) -> ()) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo(f: &dyn Fn(u32, bool) -> ()) {}

        fn main() {
            foo(|i/*caret*/, x| {});
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic Fn`() = doTest("""
        fn foo<F: Fn(u32, bool) -> ()>(f: F) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo<F: Fn(u32, bool) -> ()>(f: F) {}

        fn main() {
            foo(|i/*caret*/, x| {});
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic FnMut`() = doTest("""
        fn foo<F: FnMut(u32, bool) -> ()>(f: F) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo<F: FnMut(u32, bool) -> ()>(f: F) {}

        fn main() {
            foo(|i/*caret*/, x| {});
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic FnOnce`() = doTest("""
        fn foo<F: FnOnce(u32, bool) -> ()>(f: F) {}

        fn main() {
            foo(/*caret*/);
        }
    """, """
        fn foo<F: FnOnce(u32, bool) -> ()>(f: F) {}

        fn main() {
            foo(|i/*caret*/, x| {});
        }
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkByText(before.trimIndent(), after.trimIndent()) {
            checkCompletion()
        }
    }

    private fun doTestWithTemplate(@Language("Rust") before: String, toType: String, @Language("Rust") after: String) {
        checkByTextWithLiveTemplate(before.trimIndent(), after.trimIndent(), toType) {
            checkCompletion()
        }
    }

    private fun checkCompletion() {
        val items = myFixture.completeBasic()
        val item = items.find {
            it.presentation.itemText == "|| {}"
        } ?: error("No lambda completion found")
        myFixture.lookup.currentItem = item
        myFixture.type('\n')
    }
}
