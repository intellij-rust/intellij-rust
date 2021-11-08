/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsUnusedImportInspection

class RemoveImportFixTest : RsInspectionsTestBase(RsUnusedImportInspection::class) {
    fun `test remove use item in the middle`() = checkFixByText("Remove unused import", """
        struct A;

        mod foo {
            use crate::FOO;
            <warning>use crate::A/*caret*/;</warning>
            use crate::BAR;
        }
    """, """
        struct A;

        mod foo {
            use crate::FOO;
            use crate::BAR;
        }
    """)

    fun `test remove use speck at the beginning`() = checkFixByText("Remove unused import", """
        struct A;

        mod foo {
            use crate::{<warning>A/*caret*/</warning>, FOO, BAR};
        }
    """, """
        struct A;

        mod foo {
            use crate::{FOO, BAR};
        }
    """)

    fun `test remove use speck in the middle`() = checkFixByText("Remove unused import", """
        struct A;

        mod foo {
            use crate::{FOO, <warning>A/*caret*/</warning>, BAR};
        }
    """, """
        struct A;

        mod foo {
            use crate::{FOO, BAR};
        }
    """)

    fun `test remove use speck at the end`() = checkFixByText("Remove unused import", """
        struct A;

        mod foo {
            use crate::{FOO, BAR, <warning>A/*caret*/</warning>};
        }
    """, """
        struct A;

        mod foo {
            use crate::{FOO, BAR};
        }
    """)

    fun `test collapse parent group with single item`() = checkFixByText("Remove unused import", """
        struct A;

        mod foo {
            use crate::{FOO, <warning>A/*caret*/</warning>};
        }
    """, """
        struct A;

        mod foo {
            use crate::FOO;
        }
    """)

    fun `test collapse parent group with single qualified item`() = checkFixByText("Remove unused import", """
        struct A;
        mod inner {
            pub struct B;
        }

        mod foo {
            use crate::{<warning>A/*caret*/</warning>, inner::B};
            fn foo(b: B) {}
        }
    """, """
        struct A;
        mod inner {
            pub struct B;
        }

        mod foo {
            use crate::inner::B;
            fn foo(b: B) {}
        }
    """)
}
