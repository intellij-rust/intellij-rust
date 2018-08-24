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
class ConvertToStringFixTest : RsInspectionsTestBase(RsTypeCheckInspection()) {
    fun `test str to_string`() = checkFixByText("Convert to String using `ToString` trait", """
            fn main () {
                let _: String = <error>"Hello World!"<caret></error>;
            }
            """, """
            fn main () {
                let _: String = "Hello World!".to_string();
            }
            """)

    fun `test {integer} to_string`() = checkFixByText("Convert to String using `ToString` trait", """
            fn main () {
                let _: String = <error>42<caret></error>;
            }
            """, """
            fn main () {
                let _: String = 42.to_string();
            }
            """)

    fun `test f32 to_string`() = checkFixByText("Convert to String using `ToString` trait", """
            fn main () {
                let _: String = <error>42f32<caret></error>;
            }
            """, """
            fn main () {
                let _: String = 42f32.to_string();
            }
            """)

    fun `test struct to_string`() = checkFixByText ("Convert to String using `ToString` trait", """
        use std::fmt;

        struct A;

        impl fmt::Display for A {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { Ok(()) }
        }

        fn main () {
            let s: String = <error>A<caret></error>;
        }
    """, """
        use std::fmt;

        struct A;

        impl fmt::Display for A {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { Ok(()) }
        }

        fn main () {
            let s: String = A.to_string();
        }
    """)
}
