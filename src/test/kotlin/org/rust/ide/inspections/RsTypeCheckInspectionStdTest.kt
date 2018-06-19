/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsTypeCheckInspectionStdTest : RsInspectionsTestBase(RsTypeCheckInspection(), true) {
    // https://github.com/intellij-rust/intellij-rust/issues/2482
    fun `test issue 2482`() = checkByText("""
        fn main() {
            let string: String = "string".to_owned();
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/2460
    fun `test issue 2460`() = checkByText("""
        fn f64compare(x: &f64, y: &f64) -> ::std::cmp::Ordering {
            x.partial_cmp(y).unwrap()
        }
    """)
}
