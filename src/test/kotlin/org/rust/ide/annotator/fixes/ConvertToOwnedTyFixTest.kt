/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class ConvertToOwnedTyFixTest : RsInspectionsTestBase(RsTypeCheckInspection()) {
    fun `test B is Owned type of A`() = checkFixByText("Convert to B using `ToOwned` trait","""
        use std::borrow::Borrow;

        struct A;
        struct B;

        impl Borrow<A> for B { fn borrow(&self) -> &A { &A } }

        impl ToOwned for A {
            type Owned = B;
            fn to_owned(&self) -> B { B }
        }

        fn main () {
            let b: B = <error>A<caret></error>;
        }
    ""","""
        use std::borrow::Borrow;

        struct A;
        struct B;

        impl Borrow<A> for B { fn borrow(&self) -> &A { &A } }

        impl ToOwned for A {
            type Owned = B;
            fn to_owned(&self) -> B { B }
        }

        fn main () {
            let b: B = A.to_owned();
        }
    """)

    fun `test B is not Owned type of A`() = checkFixIsUnavailable("Convert to B using `ToOwned` trait", """
        use std::borrow::Borrow;

        struct A;
        struct B;
        struct C;

        impl Borrow<A> for C { fn borrow(&self) -> &A { &A; } }

        impl ToOwned for A {
            type Owned = C;
            fn to_owned(&self) -> C { C; }
        }

        fn main () {
            let b: B = <error>A<caret></error>;
        }
    """)

    fun `test no ToOwned impl for A`() = checkFixIsUnavailable("Convert to type B using `ToOwned` trait", """
        struct A;
        struct B;

        fn main () {
            let b: B = <error>A<caret></error>;
        }
    """)
}
