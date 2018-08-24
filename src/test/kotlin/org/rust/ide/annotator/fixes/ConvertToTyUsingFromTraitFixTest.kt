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
class ConvertToTyUsingFromTraitFixTest : RsInspectionsTestBase(RsTypeCheckInspection()) {
    fun `test B from A when impl From A for B is available`() = checkFixByText("Convert to B using `From` trait", """
        struct A{}
        struct B{}

        impl From<A> for B { fn from(item: A) -> Self {B{}} }

        fn main () {
            let b: B = <error>A {}<caret></error>;
        }
    """, """
        struct A{}
        struct B{}

        impl From<A> for B { fn from(item: A) -> Self {B{}} }

        fn main () {
            let b: B = B::from(A {});
        }
    """)

    fun `test no fix when impl From A for B is not available`() = checkFixIsUnavailable("Convert to B using `From` trait", """
        struct A{}
        struct B{}
        struct C{}

        impl From<A> for C { fn from(item: A) -> Self {C{}} }

        fn main () {
            let b: B = <error>A {}<caret></error>;
        }
    """)


    fun `test From impl provided by std lib`() = checkFixByText("Convert to u32 using `From` trait", """
        fn main () {
            let x: u32 = <error>'X'<caret></error>;
        }
    """, """
        fn main () {
            let x: u32 = u32::from('X');
        }
    """)

    fun `test From impl for generic type`() = checkFixByText ("Convert to Vec<u8> using `From` trait", """
        fn main () {
            let v: Vec<u8> = <error>String::new()<caret></error>;
        }
    """, """
        fn main () {
            let v: Vec<u8> = Vec::from(String::new());
        }
    """)

}
