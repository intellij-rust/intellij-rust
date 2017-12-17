/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.inspections.RsExperimentalChecksInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class AddAsTyFixTest : RsInspectionsTestBase(RsExperimentalChecksInspection()) {
    fun `test numeric value cast`() = checkFixByText("Add safe cast to u8", """
            fn main () {
                let _: u8 = <error>42u16<caret></error>;
            }
            """, """
            fn main () {
                let _: u8 = 42u16 as u8;
            }
            """)

    fun `test numeric inferred value cast`() = checkFixByText("Add safe cast to f32", """
            fn main () {
                let _: f32 = <error>42<caret></error>;
            }
            """, """
            fn main () {
                let _: f32 = 42 as f32;
            }
            """)

    fun `test numeric variable cast`() = checkFixByText("Add safe cast to i64", """
            fn main () {
                let x: i32 = 42;
                let y: i64 = <error>x<caret></error>;
            }
            """, """
            fn main () {
                let x: i32 = 42;
                let y: i64 = x as i64;
            }
            """)

    fun `test numeric function call result cast`() = checkFixByText("Add safe cast to f32", """
            fn answer() -> i32 {42}
            fn main () {
                let _: f32 = <error>answer()<caret></error>;
            }
            """, """
            fn answer() -> i32 {42}
            fn main () {
                let _: f32 = answer() as f32;
            }
            """)
}
