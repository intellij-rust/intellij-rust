/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.borrowck

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class RsBorrowCheckerFixesTest : RsInspectionsTestBase(RsBorrowCheckerInspection::class) {

    fun `test derive copy on struct`() = checkFixByText("Derive Copy trait", """
        struct S;

        fn main() {
            let x = S;
            let y = x;
            <error descr="Use of moved value">x<caret></error>;
        }
    """, """
        #[derive(Clone, Copy)]
        struct S;

        fn main() {
            let x = S;
            let y = x;
            x;
        }
    """, checkWarn = false)

    fun `test derive copy on struct with attr`() = checkFixByText("Derive Copy trait", """
        #[derive(PartialOrd, /* come comment */ PartialEq)]
        struct S;

        fn main() {
            let x = S;
            let y = x;
            <error descr="Use of moved value">x<caret></error>;
        }
    """, """
        #[derive(PartialOrd, /* come comment */ PartialEq, Clone, Copy)]
        struct S;

        fn main() {
            let x = S;
            let y = x;
            x;
        }
    """, checkWarn = false)

    fun `test derive copy on enum`() = checkFixByText("Derive Copy trait", """
        enum E { One }

        fn main() {
            let x = E::One;
            let y = x;
            <error descr="Use of moved value">x<caret></error>;
        }
    """, """
        #[derive(Clone, Copy)]
        enum E { One }

        fn main() {
            let x = E::One;
            let y = x;
            x;
        }
    """, checkWarn = false)

    fun `test derive copy is unavailable on non-copyable enum`() = checkFixIsUnavailable("Derive Copy trait", """
        struct S;
        enum E { One, Two(S) }

        fn main() {
            let x = E::One;
            let y = x;
            <error descr="Use of moved value">x<caret></error>;
        }
    """, checkWarn = false)

    fun `test derive copy is unavailable on non-copyable struct`() = checkFixIsUnavailable("Derive Copy trait", """
        struct T;
        struct S { data: T }

        fn main() {
            let x = S { data: T };
            x.data;
            <error descr="Use of moved value">x.data</error><caret>;
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test derive copy on struct with impl clone`() = checkFixByText("Derive Copy trait", """
        struct S;
        impl Clone for S { fn clone(&self) -> S { unimplemented!() } }

        fn main() {
            let x = S;
            let y = x;
            <error descr="Use of moved value">x<caret></error>;
        }
    """, """
        #[derive(Copy)]
        struct S;
        impl Clone for S { fn clone(&self) -> S { unimplemented!() } }

        fn main() {
            let x = S;
            let y = x;
            x;
        }
    """, checkWarn = false)
}
