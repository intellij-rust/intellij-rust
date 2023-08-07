/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsReplaceCastWithSuffixInspection

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)// for arithmetic type inference
class ReplaceCastWithSuffixTest : RsInspectionsTestBase(RsReplaceCastWithSuffixInspection::class) {
    fun `test integer cast`() = checkFixByText("Replace with `1i32`", """
        fn foo() {
            let a = /*weak_warning*/1 /*caret*/as i32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = 1i32;
        }
    """, checkWeakWarn = true)

    fun `test negative integer cast`() = checkFixByText("Replace with `-1i32`", """
        fn foo() {
            let a = /*weak_warning*/-1 /*caret*/as i32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = -1i32;
        }
    """, checkWeakWarn = true)

    fun `test float cast`() = checkFixByText("Replace with `1.0f64`", """
        fn foo() {
            let a = /*weak_warning*/1.0 /*caret*/as f64/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = 1.0f64;
        }
    """, checkWeakWarn = true)

    fun `test negative float cast`() = checkFixByText("Replace with `-1.0f32`", """
        fn foo() {
            let a = /*weak_warning*/-1.0 /*caret*/as f32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = -1.0f32;
        }
    """, checkWeakWarn = true)

    fun `test isize cast`() = checkFixByText("Replace with `1isize`", """
        fn foo() {
            let a = /*weak_warning*/1 /*caret*/as isize/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = 1isize;
        }
    """, checkWeakWarn = true)

    fun `test hex cast`() = checkFixByText("Replace with `0xffi32`", """
        fn foo() {
            let a = /*weak_warning*/0xff /*caret*/as i32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = 0xffi32;
        }
    """, checkWeakWarn = true)
}
