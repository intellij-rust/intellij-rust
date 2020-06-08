/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsMainFunctionNotFoundInspection

class AddMainFnFixTest : RsInspectionsTestBase(RsMainFunctionNotFoundInspection::class) {

    fun `test fix`() = checkFixByFileTree("Add `fn main()`", """
    //- main.rs
        <error descr="`main` function not found in crate `test-package` [E0601]"> /*caret*/ </error>
    """, """
    //- main.rs
        fn main() {/*caret*/}
    """)

    fun `test the fix in a custom bin`() = checkFixByFileTree("Add `fn main()`", """
    //- bin/a.rs
        <error descr="`main` function not found in crate `test-package` [E0601]"> /*caret*/ </error>
    """, """
    //- bin/a.rs
        fn main() {/*caret*/}
    """)

    fun `test the fix in a custom example bin`() = checkFixByFileTree("Add `fn main()`", """
    //- example/a.rs
        <error descr="`main` function not found in crate `test-package` [E0601]"> /*caret*/ </error>
    """, """
    //- example/a.rs
        fn main() {/*caret*/}
    """)
}
