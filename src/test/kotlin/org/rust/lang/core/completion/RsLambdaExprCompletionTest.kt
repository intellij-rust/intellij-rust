/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
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
            foo(|p0| {/*caret*/});
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
            foo(|p0/*caret*/, p1| {});
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
            foo(|p0/*caret*/, p1| {});
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
            foo(|p0/*caret*/, p1| {});
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
            foo(|p0/*caret*/, p1| {});
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
            foo(|p0/*caret*/, p1| {});
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
            foo(|p0/*caret*/, p1| {});
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
            val presentation = LookupElementPresentation()
            it.renderElement(presentation)
            presentation.itemText == "|| {}"
        } ?: error("No lambda completion found")
        myFixture.lookup.currentItem = item
        myFixture.type('\n')
    }
}
