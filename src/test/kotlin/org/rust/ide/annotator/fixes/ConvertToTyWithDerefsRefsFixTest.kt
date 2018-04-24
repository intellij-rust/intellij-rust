/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ConvertToTyWithDerefsRefsFixTest : RsInspectionsTestBase(RsTypeCheckInspection()) {

    fun `test &T to T `() = checkFixByText("Convert to i32 using dereferences and/or references", """
        fn main () {
            let a: &i32 = &42;
            let b: i32 = <error>a<caret></error>;
        }
    """, """
        fn main () {
            let a: &i32 = &42;
            let b: i32 = *a;
        }
    """)

    fun `test &mut T to T `() = checkFixByText("Convert to i32 using dereferences and/or references", """
        fn main () {
            let a: &mut i32 = &mut 42;
            let b: i32 = <error>a<caret></error>;
        }
    """, """
        fn main () {
            let a: &mut i32 = &mut 42;
            let b: i32 = *a;
        }
    """)

    fun `test &&mut T to T`() = checkFixByText("Convert to i32 using dereferences and/or references", """
        fn main () {
            let a: &&mut i32 = &&mut 42;
            let b: i32 = <error>a<caret></error>;
        }
    """, """
        fn main () {
            let a: &&mut i32 = &&mut 42;
            let b: i32 = **a;
        }
    """)

    fun `test T to &T `() = checkFixByText("Convert to &i32 using dereferences and/or references", """
        fn main () {
            let a: i32 = 42;
            let b: &i32 = <error>a<caret></error>;
        }
    """, """
        fn main () {
            let a: i32 = 42;
            let b: &i32 = &a;
        }
    """)

    fun `test mut T to &mut T `() = checkFixByText("Convert to &mut i32 using dereferences and/or references", """
        fn main () {
            let mut a: i32 = 42;
            let b: &mut i32 = <error>a<caret></error>;
        }
    """, """
        fn main () {
            let mut a: i32 = 42;
            let b: &mut i32 = &mut a;
        }
    """)

    fun `test T to &mut T `() = checkFixIsUnavailable("Convert to &mut i32 using dereferences and/or references", """
        fn main () {
            let a: i32 = 42;
            let b: &mut i32 = <error>a<caret></error>;
        }
    """)

    fun `test T to &mut &T `() = checkFixByText("Convert to &mut &i32 using dereferences and/or references", """
        fn main () {
            let a: i32 = 42;
            let b: &mut &i32 = <error>a<caret></error>;
        }
    """, """
        fn main () {
            let a: i32 = 42;
            let b: &mut &i32 = &mut &a;
        }
    """)

    fun `test &T to &mut T`() = checkFixIsUnavailable("Convert to &mut i32 using dereferences and/or references", """
        fn main () {
            let a: &i32 = &42;
            let b: &mut i32 = <error>a<caret></error>;
        }
    """)

    fun `test mut &T to &mut T`() = checkFixIsUnavailable("Convert to &mut i32 using dereferences and/or references", """
        fn main () {
            let mut a: &i32 = &42;
            let b: &mut i32 = <error>a<caret></error>;
        }
    """)

    fun `test &mut&&mut T to &mut T`() = checkFixIsUnavailable("Convert to &mut i32 using dereferences and/or references", """
        fn main () {
            let a: &i32 = &42;
            let b: &mut i32 = <error>a<caret></error>;
        }
    """)

    fun `test &mut&&mut T to &mut& T `() = checkFixByText("Convert to &mut &i32 using dereferences and/or references", """
        fn main () {
            let a: &mut &&mut i32 = &mut &&mut 42;
            let b: &mut &i32 = <error>a<caret></error>;
        }
    ""","""
        fn main () {
            let a: &mut &&mut i32 = &mut &&mut 42;
            let b: &mut &i32 = &mut &***a;
        }
    """)

    fun `test B to &mut A when Deref for A with target B exists`() = checkFixIsUnavailable("Convert to &mut B using dereferences and/or references", """
        #[lang = "deref"]
        trait Deref { type Target; }
        struct A;
        struct B;
        impl Deref for A { type Target = B; }

        fn main () {
            let a: A = A;
            let b: &mut B = <error>a<caret></error>;
        }
    """)

    fun `test mut B to &mut A when Deref for A with target B exists`() = checkFixByText("Convert to &mut B using dereferences and/or references", """
        #[lang = "deref"]
        trait Deref { type Target; }
        struct A;
        struct B;
        impl Deref for A { type Target = B; }

        fn main () {
            let mut a: A = A;
            let b: &mut B = <error>a<caret></error>;
        }
    """, """
        #[lang = "deref"]
        trait Deref { type Target; }
        struct A;
        struct B;
        impl Deref for A { type Target = B; }

        fn main () {
            let mut a: A = A;
            let b: &mut B = &mut *a;
        }
    """)

}

