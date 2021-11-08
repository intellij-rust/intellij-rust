/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck

import org.rust.MockAdditionalCfgOptions
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ChangeReprAttributeFixTest : RsInspectionsTestBase(RsTypeCheckInspection::class) {
    fun `test add repr on enum`() = checkFixByText("Change representation of enum `Foo` to `#[repr(u16)]`", """
        enum Foo {
            FooVariant = <error>1u16/*caret*/</error>
        }
    """, """
        #[repr(u16)]
        enum Foo {
            FooVariant = 1u16
        }
    """)

    fun `test change repr on enum`() = checkFixByText("Change representation of enum `Foo` to `#[repr(u16)]`", """
        #[repr(u8)]
        enum Foo {
            FooVariant = <error>1u16/*caret*/</error>
        }
    """, """
        #[repr(u16)]
        enum Foo {
            FooVariant = 1u16
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test change cfg_attr repr on enum`() = checkFixByText("Change representation of enum `Foo` to `#[repr(u16)]`", """
        #[cfg_attr(intellij_rust, repr(u8))]
        enum Foo {
            FooVariant = <error>1u16/*caret*/</error>
        }
    """, """
        #[cfg_attr(intellij_rust, repr(u16))]
        enum Foo {
            FooVariant = 1u16
        }
    """)

    fun `test aliased types`() = checkFixByText("Change representation of enum `Foo` to `#[repr(u8)]`", """
        type A = u8;

        enum Foo {
            FooVariant = <error>1 as A/*caret*/</error>
        }
    """, """
        type A = u8;

        #[repr(u8)]
        enum Foo {
            FooVariant = 1 as A
        }
    """)

    fun `test don't offer the fix for non-integral types`() = checkFixIsUnavailable("Change representation of enum `Foo` to `#[repr(char)]`", """
        enum Foo {
            FooVariant = <error>'a'/*caret*/</error>
        }
    """)

    fun `test don't offer the fix for nested expressions`() = checkFixIsUnavailable("Change representation of enum `Foo` to `#[repr(u8)]`", """
        enum Foo {
            FooVariant = {
                let x: isize = <error>1u8/*caret*/</error>;
                2
            }
        }
    """)
}
