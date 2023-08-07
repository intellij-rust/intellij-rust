/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnnecessaryQualificationsInspectionTest : RsInspectionsTestBase(RsUnnecessaryQualificationsInspection::class) {
    fun `test unavailable for single segment path`() = checkWarnings("""
        struct S;

        fn foo() {
            let _: S;
        }
    """)

    fun `test simple segment`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">bar::/*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    fun `test multiple path necessary qualified`() = checkFixIsUnavailable("Remove unnecessary path prefix", """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        fn foo() {
            let _: bar::baz::/*caret*/S;
        }
    """, checkWeakWarn = true)

    fun `test multiple segments whole path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">bar::baz::/*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    fun `test multiple segments partial path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">bar::/*caret*/</weak_warning>baz::S;
        }
    """, """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz;

        fn foo() {
            let _: /*caret*/baz::S;
        }
    """, checkWeakWarn = true)

    fun `test associated method`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
            impl S {
                fn new() -> S { S }
            }
        }

        use bar::S;

        fn foo() {
            let _ = <weak_warning descr="Unnecessary qualification">bar::/*caret*/</weak_warning>S::new();
        }
    """, """
        mod bar {
            pub struct S;
            impl S {
                fn new() -> S { S }
            }
        }

        use bar::S;

        fn foo() {
            let _ = /*caret*/S::new();
        }
    """, checkWeakWarn = true)

    fun `test expression context with generics`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S<T>(T);
            impl <T> S<T> {
                fn new(t: T) -> S<T> { S(t) }
            }
        }

        use bar::S;

        fn foo() {
            let _ = <weak_warning descr="Unnecessary qualification">bar::/*caret*/</weak_warning>S::<u32>::new(0);
        }
    """, """
        mod bar {
            pub struct S<T>(T);
            impl <T> S<T> {
                fn new(t: T) -> S<T> { S(t) }
            }
        }

        use bar::S;

        fn foo() {
            let _ = /*caret*/S::<u32>::new(0);
        }
    """, checkWeakWarn = true)

    fun `test crate prefix`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">crate::/*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    @MockEdition(CargoWorkspace.Edition.EDITION_2015)
    fun `test bare colon colon`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">::/*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    @MockEdition(CargoWorkspace.Edition.EDITION_2015)
    fun `test bare colon colon with nested path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">::bar::/*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test bare colon colon necessary qualification`() = checkFixIsUnavailableByFileTree("Remove unnecessary path prefix", """
        //- dep-lib/lib.rs
        pub mod bar {
            pub struct S;
        }

        //- main.rs
        pub mod dep_lib {
            pub mod bar {
                pub struct S;
            }
        }

        fn foo() {
            let _: ::dep_lib::bar::/*caret*/S;
        }
    """, checkWeakWarn = true)

    fun `test spaces in path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">bar ::   /*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    fun `test comments in path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">bar/*foo*/ :: /*bar*//*caret*/</weak_warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: /*caret*/S;
        }
    """, checkWeakWarn = true)

    fun `test path in value namespace`() = checkFixByText("Remove unnecessary path prefix", """
        enum Foo {
            A,
            B
        }

        use Foo::B;

        fn foo() {
            let _ = <weak_warning descr="Unnecessary qualification">Foo::/*caret*/</weak_warning>B;
        }
    """, """
        enum Foo {
            A,
            B
        }

        use Foo::B;

        fn foo() {
            let _ = /*caret*/B;
        }
    """, checkWeakWarn = true)

    fun `test path with type arguments 1`() = checkWarnings("""
        enum Foo<T> {
            A(T),
            B
        }

        use Foo::B;

        fn foo() {
            let _ = Foo::<()>::B;
        }
    """)

    fun `test path with type arguments 2`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub enum Foo<T> {
                A(T),
                B
            }
        }

        use bar::Foo;

        fn foo() {
            let _ = <weak_warning descr="Unnecessary qualification">bar::/*caret*/</weak_warning>Foo::<()>::B;
        }
    """, """
        mod bar {
            pub enum Foo<T> {
                A(T),
                B
            }
        }

        use bar::Foo;

        fn foo() {
            let _ = /*caret*/Foo::<()>::B;
        }
    """, checkWeakWarn = true)

    fun `test allow`() = checkWarnings("""
        #![allow(unused_qualifications)]

        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: bar::S;
        }
    """)

    fun `test deny`() = checkWarnings("""
        #![deny(unused_qualifications)]

        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <error descr="Unnecessary qualification">bar::/*caret*/</error>S;
        }
    """)

    fun `test deny 'unused' does not make the unused_qualifications a hard error`() = checkWarnings("""
        #![deny(unused)]

        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <weak_warning descr="Unnecessary qualification">bar::/*caret*/</weak_warning>S;
        }
    """)
}
