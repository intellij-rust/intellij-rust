/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsExperimentalChecksInspectionTest : RsInspectionsTestBase(RsExperimentalChecksInspection()) {
    fun `test E0614 type cannot be dereferenced`() = checkByText("""
        fn main() {
            let _ = <error>*0</error>;
        }
    """)

    fun `test no "E0614 type cannot be dereferenced" when trying to dereference unknown type`() = checkByText("""
        fn main() {
            let a = SomeUnknownType;
            let _ = *a;
        }
    """)
}
