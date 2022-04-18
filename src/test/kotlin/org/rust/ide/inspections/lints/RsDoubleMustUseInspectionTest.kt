/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

class RsDoubleMustUseInspectionTest : RsInspectionsTestBase(RsDoubleMustUseInspection::class) {
    fun `test double must_use with outer attr`() = checkFixByText("Remove `#[must_use]` from the function", """
        #[must_use]
        struct S;

        <weak_warning descr="This function has a `#[must_use]` attribute, but returns a type already marked as `#[must_use]`">/*caret*/#[must_use]</weak_warning>
        fn foo() -> S { S }

        fn main() {}
    """, """
        #[must_use]
        struct S;

        fn foo() -> S { S }

        fn main() {}
    """)

    fun `test double must_use with inner attr`() = checkFixByText("Remove `#[must_use]` from the function", """
        #[must_use]
        struct S;

        fn foo() -> S {
            <weak_warning descr="This function has a `#[must_use]` attribute, but returns a type already marked as `#[must_use]`">/*caret*/#![must_use]</weak_warning>
            S
        }

        fn main() {}
    """, """
        #[must_use]
        struct S;

        fn foo() -> S {
            S
        }

        fn main() {}
    """)
}
