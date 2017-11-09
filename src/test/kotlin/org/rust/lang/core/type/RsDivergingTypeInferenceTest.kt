/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type


class RsDivergingTypeInferenceTest : RsTypificationTestBase() {
    fun `test if with all branches diverging`() = testExpr("""
        fn main() {
            let x = if true { return; } else { return; };
            x;
        } //^ !
    """)

    fun `test one branch diverges`() = testExpr("""
        fn main() {
            let x = if true { return; } else { 92 };
            x;
        } //^ i32
    """)

    fun `test block diverges with explicit type annotation`() = testExpr("""
        fn main() {
            let x = { let _: i32 = loop {}; 92 };
            x;
        } //^ !
    """)
}
