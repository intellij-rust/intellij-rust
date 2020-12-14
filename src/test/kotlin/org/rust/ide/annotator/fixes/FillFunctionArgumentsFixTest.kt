/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class FillFunctionArgumentsFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test simple call`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32) {}

        fn main() {
            foo(<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32) {}

        fn main() {
            foo(0);
        }
    """)

    fun `test multiple arguments`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: u32, c: &str) {}

        fn main() {
            foo(1<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: u32, c: &str) {}

        fn main() {
            foo(1, 0, "");
        }
    """)

    fun `test ufcs self value`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(self, a: u32) {}
        }
        fn foo() {
            S::foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(self, a: u32) {}
        }
        fn foo() {
            S::foo(S, 0);
        }
    """)

    fun `test ufcs self ref`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(&self, a: u32) {}
        }
        fn foo() {
            S::foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&self, a: u32) {}
        }
        fn foo() {
            S::foo(&S, 0);
        }
    """)

    fun `test ufcs self mut ref`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(&mut self, a: u32) {}
        }
        fn foo() {
            S::foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut self, a: u32) {}
        }
        fn foo() {
            S::foo(&mut S, 0);
        }
    """)

    fun `test ufcs generic trait bound`() = checkFixByText("Fill missing arguments", """
        trait Trait {
            fn foo(&mut self, a: u32) {}
        }
        fn foo<T: Trait>() {
            T::foo(<error>/*caret*/)</error>;
        }
    """, """
        trait Trait {
            fn foo(&mut self, a: u32) {}
        }
        fn foo<T: Trait>() {
            T::foo(&mut (), 0);
        }
    """)

    fun `test method call`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(&self, a: u32, b: u32) {}
        }
        fn foo(s: S) {
            s.foo(1<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&self, a: u32, b: u32) {}
        }
        fn foo(s: S) {
            s.foo(1, 0);
        }
    """)

    fun `test tuple struct`() = checkFixByText("Fill missing arguments", """
        struct S(u32, u32);
        fn foo() {
            S(<error>/*caret*/)</error>;
        }
    """, """
        struct S(u32, u32);
        fn foo() {
            S(0, 0);
        }
    """)
}
