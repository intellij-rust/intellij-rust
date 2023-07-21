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
    """, checkWeakWarn = true)

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
    """, checkWeakWarn = true)

    fun `test double must_use with normalizable associated type`() = checkFixByText("Remove `#[must_use]` from the function", """
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = S; }

        #[must_use]
        struct S;

        <weak_warning descr="This function has a `#[must_use]` attribute, but returns a type already marked as `#[must_use]`">/*caret*/#[must_use]</weak_warning>
        fn foo() -> <Struct as Trait>::Item { S }

        fn main() {}
    """, """
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = S; }

        #[must_use]
        struct S;

        fn foo() -> <Struct as Trait>::Item { S }

        fn main() {}
    """, checkWeakWarn = true)
}
