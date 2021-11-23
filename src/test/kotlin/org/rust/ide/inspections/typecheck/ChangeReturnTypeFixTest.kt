/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
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

    fun `test default type argument`() = checkFixByText("Change return type of function 'foo' to 'S'", """
        struct S<T = u32>(T);

        fn foo(a: S) {
            <error>a<caret></error>
        }
    """, """
        struct S<T = u32>(T);

        fn foo(a: S) -> S {
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
        use a::{A, bar, S};

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
        use a::{A, bar, S};

        mod a {
            pub struct S;
            pub type A = S;
            pub fn bar() -> (S, A) { (S, A) }
        }

        fn foo() -> (S, A) {
            bar()
        }
    """)

    fun `test import skip default type argument`() = checkFixByText("Change return type of function 'foo' to 'S'", """
        use a::bar;

        mod a {
            pub struct R;
            pub struct S<T = R>(T);
            pub fn bar() -> S { S(R) }
        }

        fn foo() -> i32 {
            <error>bar()<caret></error>
        }
    """, """
        use a::{bar, S};

        mod a {
            pub struct R;
            pub struct S<T = R>(T);
            pub fn bar() -> S { S(R) }
        }

        fn foo() -> S {
            bar()
        }
    """)

    fun `test import aliased type`() = checkFixByText("Change return type of function 'foo' to 'S'", """
        use a::bar;

        mod a {
            pub struct R;
            pub type S = R;
            pub fn bar() -> S { R }
        }

        fn foo() -> i32 {
            <error>bar()<caret></error>
        }
    """, """
        use a::{bar, S};

        mod a {
            pub struct R;
            pub type S = R;
            pub fn bar() -> S { R }
        }

        fn foo() -> S {
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

    fun `test use qualified name for ambiguous type`() = checkFixByText("Change return type of function 'foo' to 'a::Foo'", """
        mod a {
            pub struct Foo;
        }
        struct Foo;

        fn foo() -> Foo {
            <error>a::Foo<caret></error>
        }
    """, """
        mod a {
            pub struct Foo;
        }
        struct Foo;

        fn foo() -> a::Foo {
            a::Foo
        }
    """)

    fun `test use qualified name for ambiguous nested type`() = checkFixByText("Change return type of function 'foo' to '(a::S, b::S)'", """
        struct S;
        mod a {
            pub struct S;
        }
        mod b {
            pub struct S;
        }

        fn foo() {
            <error>(a::S<caret>, b::S)</error>
        }
    """, """
        struct S;
        mod a {
            pub struct S;
        }
        mod b {
            pub struct S;
        }

        fn foo() -> (a::S, b::S) {
            (a::S, b::S)
        }
    """)

    fun `test do not qualify type parameter`() = checkFixByText("Change return type of function 'foo' to 'b::S<T>'", """
        struct T;
        struct S<T>(T);
        mod b {
            pub struct S<T>(T);
        }

        fn foo() -> S<T> {
            <error>b::S::<T>(T)<caret></error>
        }
    """, """
        struct T;
        struct S<T>(T);
        mod b {
            pub struct S<T>(T);
        }

        fn foo() -> b::S<T> {
            b::S::<T>(T)
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test qualify ambiguous type with reexported path`() = checkFixByText("Change return type of function 'foo' to 'crate::a::S'", """
        struct S;
        mod a {
            pub use b::S;

            mod b {
                pub struct S;
            }
        }

        fn foo() -> S {
            <error>a::S<caret></error>
        }
    """, """
        struct S;
        mod a {
            pub use b::S;

            mod b {
                pub struct S;
            }
        }

        fn foo() -> crate::a::S {
            a::S
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test use correct path 1`() = checkFixByFileTree("Change return type of function 'func' to 'Foo'", """
    //- lib.rs
        pub struct Foo;
    //- main.rs
        pub use test_package::Foo;
        mod inner {
            fn func() {
                <error>test_package::Foo/*caret*/</error>
            }
        }
    """, """
    //- lib.rs
        pub struct Foo;
    //- main.rs
        pub use test_package::Foo;
        mod inner {
            use test_package::Foo;

            fn func() -> Foo {
                test_package::Foo/*caret*/
            }
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test use correct path 2`() = checkFixByFileTree("Change return type of function 'func' to 'test_package::Foo'", """
    //- lib.rs
        pub struct Foo;
    //- main.rs
        pub use test_package::Foo;
        mod inner {
            struct Foo;
            fn func() {
                <error>test_package::Foo/*caret*/</error>
            }
        }
    """, """
    //- lib.rs
        pub struct Foo;
    //- main.rs
        pub use test_package::Foo;
        mod inner {
            struct Foo;
            fn func() -> test_package::Foo {
                test_package::Foo/*caret*/
            }
        }
    """)
}
