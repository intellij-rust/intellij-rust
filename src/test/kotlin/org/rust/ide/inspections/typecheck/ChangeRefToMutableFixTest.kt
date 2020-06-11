/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ChangeRefToMutableFixTest : RsInspectionsTestBase(RsTypeCheckInspection::class) {
    fun `test simple`() = checkFixByText("Change reference to mutable", """
        fn foo(t: &mut u32) {}
        fn bar() {
            let mut x: u32 = 5;
            foo(/*caret*/<error>&x</error>);
        }
    """, """
        fn foo(t: &mut u32) {}
        fn bar() {
            let mut x: u32 = 5;
            foo(&mut x);
        }
    """)

    fun `test immutable variable`() = checkFixByText("Change reference to mutable", """
        fn foo(t: &mut u32) {}
        fn bar() {
            let x: u32 = 5;
            foo(/*caret*/<error>&x</error>);
        }
    """, """
        fn foo(t: &mut u32) {}
        fn bar() {
            let x: u32 = 5;
            foo(&mut x);
        }
    """)

    fun `test nested references`() = checkFixByText("Change reference to mutable", """
        fn foo(t: &mut &u32) {}
        fn bar() {
            let mut x: u32 = 5;
            foo(/*caret*/<error>&x</error>);
        }
    """, """
        fn foo(t: &mut &u32) {}
        fn bar() {
            let mut x: u32 = 5;
            foo(&mut x);
        }
    """)

    fun `test unknown inner type`() = checkFixByText("Change reference to mutable", """
        struct S<T>(T);
        impl<T> S<T> {
            fn new() -> Self { unreachable!() }
        }

        fn foo(t: &mut S<u32>) {}
        fn bar() {
            let mut x = S::new();
            foo(/*caret*/<error>&x</error>);
        }
    """, """
        struct S<T>(T);
        impl<T> S<T> {
            fn new() -> Self { unreachable!() }
        }

        fn foo(t: &mut S<u32>) {}
        fn bar() {
            let mut x = S::new();
            foo(&mut x);
        }
    """)

    fun `test unavailable on mut reference`() = checkFixIsUnavailable("Change reference to mutable", """
        fn foo(t: &mut u32) {}
        fn bar() {
            let mut x: u32 = 5;
            foo(/*caret*/&mut x);
        }
    """)
}
