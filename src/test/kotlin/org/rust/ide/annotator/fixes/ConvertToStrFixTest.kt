/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.inspections.RsExperimentalChecksInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class ConvertToStrFixTest : RsInspectionsTestBase(RsExperimentalChecksInspection()) {
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test String to &str`() = checkFixByText("Convert to &str using `as_str` method", """
            fn main () {
                let _: &str = <error>String::from("Hello World!")<caret></error>;
            }
            """, """
            fn main () {
                let _: &str = String::from("Hello World!").as_str();
            }
            """)

    fun `test String to &mut str`() = checkFixByText("Convert to &mut str using `as_mut_str` method", """
            fn main () {
                let _: &mut str = <error>String::from("Hello World!")<caret></error>;
            }
            """, """
            fn main () {
                let _: &mut str = String::from("Hello World!").as_mut_str();
            }
            """)
}
