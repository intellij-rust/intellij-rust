/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

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
            let _: <warning descr="Unnecessary qualification">bar::/*caret*/</warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: S;
        }
    """)

    fun `test multiple segments whole path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz::S;

        fn foo() {
            let _: <warning descr="Unnecessary qualification">bar::baz::/*caret*/</warning>S;
        }
    """, """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz::S;

        fn foo() {
            let _: S;
        }
    """)

    fun `test multiple segments partial path`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz;

        fn foo() {
            let _: <warning descr="Unnecessary qualification">bar::/*caret*/</warning>baz::S;
        }
    """, """
        mod bar {
            pub mod baz {
                pub struct S;
            }
        }

        use bar::baz;

        fn foo() {
            let _: baz::S;
        }
    """)

    fun `test associated method`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
            impl S {
                fn new() -> S { S }
            }
        }

        use bar::S;

        fn foo() {
            let _ = <warning descr="Unnecessary qualification">bar::/*caret*/</warning>S::new();
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
            let _ = S::new();
        }
    """)

    fun `test expression context with generics`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S<T>(T);
            impl <T> S<T> {
                fn new(t: T) -> S<T> { S(t) }
            }
        }

        use bar::S;

        fn foo() {
            let _ = <warning descr="Unnecessary qualification">bar::/*caret*/</warning>S::<u32>::new(0);
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
            let _ = S::<u32>::new(0);
        }
    """)

    fun `test crate prefix`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <warning descr="Unnecessary qualification">crate::/*caret*/</warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: S;
        }
    """)

    fun `test bare colon colon`() = checkFixByText("Remove unnecessary path prefix", """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: <warning descr="Unnecessary qualification">::/*caret*/</warning>S;
        }
    """, """
        mod bar {
            pub struct S;
        }

        use bar::S;

        fn foo() {
            let _: S;
        }
    """)

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
}
