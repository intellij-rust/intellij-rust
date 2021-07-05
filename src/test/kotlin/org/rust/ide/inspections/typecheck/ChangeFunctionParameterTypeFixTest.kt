/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ChangeFunctionParameterTypeFixTest : RsInspectionsTestBase(RsTypeCheckInspection::class) {
    fun `test unavailable on method UFCS self argument`() = checkFixIsUnavailable("Change type of", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            S::foo(<error>1/*caret*/</error>, 1);
        }
    """)

    fun `test unavailable with disabled parameter`() = checkFixIsUnavailable("Change type", """
        fn foo(a: u32, #[cfg(foo)] b: u32, c: u32) {}
        fn bar() {
            foo(1, <error>true/*caret*/</error>);
        }
    """)

    fun `test simple binding`() = checkFixByText("Change type of parameter `a` of function `foo` to `bool`", """
        fn foo(a: u32) {}
        fn bar() {
            foo(<error>true/*caret*/</error>);
        }
    """, """
        fn foo(a: bool) {}
        fn bar() {
            foo(true);
        }
    """)

    fun `test complex binding`() = checkFixByText("Change type of `1st` parameter of function `foo` to `bool`", """
        fn foo((a, b): (u32, u32)) {}
        fn bar() {
            foo(<error>true/*caret*/</error>);
        }
    """, """
        fn foo((a, b): bool) {}
        fn bar() {
            foo(true);
        }
    """)

    fun `test parameter in the middle`() = checkFixByText("Change type of `2nd` parameter of function `foo` to `bool`", """
        fn foo(x: u32, (a, b): (u32, u32), c: u32) {}
        fn bar() {
            foo(0, <error>true/*caret*/</error>, 1);
        }
    """, """
        fn foo(x: u32, (a, b): bool, c: u32) {}
        fn bar() {
            foo(0, true, 1);
        }
    """)

    fun `test extra arguments`() = checkFixByText("Change type of parameter `a` of function `foo` to `bool`", """
        fn foo(a: u32) {}
        fn bar() {
            foo(<error>true/*caret*/</error>, 1);
        }
    """, """
        fn foo(a: bool) {}
        fn bar() {
            foo(true, 1);
        }
    """)

    fun `test missing arguments`() = checkFixByText("Change type of parameter `a` of function `foo` to `bool`", """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo(<error>true/*caret*/</error>);
        }
    """, """
        fn foo(a: bool, b: u32) {}
        fn bar() {
            foo(true);
        }
    """)

    fun `test method`() = checkFixByText("Change type of parameter `a` of method `foo` to `&str`", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            s.foo(<error>""/*caret*/</error>);
        }
    """, """
        struct S {}
        impl S {
            fn foo(&self, a: &str) {}
        }

        fn bar(s: S) {
            s.foo("");
        }
    """)

    fun `test method UFCS`() = checkFixByText("Change type of parameter `a` of method `foo` to `&str`", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            S::foo(&s, <error>""/*caret*/</error>);
        }
    """, """
        struct S {}
        impl S {
            fn foo(&self, a: &str) {}
        }

        fn bar(s: S) {
            S::foo(&s, "");
        }
    """)
}
