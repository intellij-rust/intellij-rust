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
}
