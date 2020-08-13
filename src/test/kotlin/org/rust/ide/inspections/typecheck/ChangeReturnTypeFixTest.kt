/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ChangeReturnTypeFixTest : RsInspectionsTestBase(RsTypeCheckInspection::class) {

    fun `test str vs () in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() {
            <error>"Hello World!"<caret></error>
        }
    """, """
        fn foo() -> &'static str {
            "Hello World!"
        }
    """)

    fun `test return str vs () in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() {
            return <error>"Hello World!"<caret></error>;
        }
    """, """
        fn foo() -> &'static str {
            return "Hello World!";
        }
    """)

    fun `test str vs i32 in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() -> i32 {
            <error>"Hello World!"<caret></error>
        }
    """, """
        fn foo() -> &'static str {
            "Hello World!"
        }
    """)

    fun `test return str vs i32 in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() -> i32 {
            return <error>"Hello World!"<caret></error>;
        }
    """, """
        fn foo() -> &'static str {
            return "Hello World!";
        }
    """)

    fun `test str vs i32 in closure`() = checkFixByText("Change return type of closure to '&str'", """
        fn foo() {
            let _ = || -> i32 <error>"Hello World!"<caret></error>;
        }
    """, """
        fn foo() {
            let _ = || -> &'static str "Hello World!";
        }
    """)

    fun `test return str vs i32 in closure`() = checkFixByText("Change return type of closure to '&str'", """
        fn foo() {
            let _ = || -> i32 { return <error>"Hello World!"<caret></error>; };
        }
    """, """
        fn foo() {
            let _ = || -> &'static str { return "Hello World!"; };
        }
    """)

    fun `test str vs i32 in method`() = checkFixByText("Change return type of method 'foo' to '&str'", """
        struct S;
        impl S {
            fn foo(&self) -> i32 {
                <error>"Hello World!"<caret></error>
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) -> &'static str {
                "Hello World!"
            }
        }
    """)

    fun `test don't show () return type`() = checkFixByText("Change return type of function 'foo' to '()'", """
        fn foo() -> i32 {
            <error>()<caret></error>
        }
    """, """
        fn foo() {
            ()
        }
    """)

    fun `test alias`() = checkFixByText("Change return type of function 'foo' to 'A'", """
        struct S;
        type A = S;
        fn foo(a: A) {
            <error>a<caret></error>
        }
    """, """
        struct S;
        type A = S;
        fn foo(a: A) -> A {
            a
        }
    """)

    fun `test import unresolved type (add)`() = checkFixByText("Change return type of function 'foo' to '(S, A)'", """
        use a::bar;

        mod a {
            pub struct S;
            pub type A = S;
            pub fn bar() -> (S, A) { (S, A) }
        }

        fn foo() {
            <error>bar()<caret></error>
        }
    """, """
        use a::{bar, A, S};

        mod a {
            pub struct S;
            pub type A = S;
            pub fn bar() -> (S, A) { (S, A) }
        }

        fn foo() -> (S, A) {
            bar()
        }
    """)

    fun `test import unresolved type (replace)`() = checkFixByText("Change return type of function 'foo' to '(S, A)'", """
        use a::bar;

        mod a {
            pub struct S;
            pub type A = S;
            pub fn bar() -> (S, A) { (S, A) }
        }

        fn foo() -> i32 {
            <error>bar()<caret></error>
        }
    """, """
        use a::{bar, A, S};

        mod a {
            pub struct S;
            pub type A = S;
            pub fn bar() -> (S, A) { (S, A) }
        }

        fn foo() -> (S, A) {
            bar()
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test do not offer fix in closure without an explicit return type passed to a function`() = checkFixIsUnavailable("Change return type of closure", """
        fn foo<F>(f: F) -> i32
            where F: Fn(i32) -> i32 {
            f(0)
        }
        fn bar() {
            foo(|x| <error>true<caret></error>);
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test do not offer fix in closure without an explicit return type assigned to a variable`() = checkFixIsUnavailable("Change return type of closure", """
        fn foo() {
            let x: fn() -> i32 = || <error>true<caret></error>;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test offer fix in closure with an explicit return type`() = checkFixByText("Change return type of closure", """
        fn foo() {
            let x: fn() -> i32 = || -> i32 <error>true<caret></error>;
        }
    """, """
        fn foo() {
            let x: fn() -> i32 = || -> bool true;
        }
    """)
}
