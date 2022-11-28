/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

class RsSSRConstantTest : RsSSRTestBase() {
    fun `test ConstantItem IDENTIFIER`() = doTest("""
        const x1: i16 = 0;
        <warning descr="">const x2: i32 = 0;</warning>
        const x3: u32 = 0;
    """, """const x2 : '_ = '_;""")

    fun `test ConstantItem Type`() = doTest("""
        const x1: i16 = 0;
        <warning descr="">const x2: i32 = 0;</warning>
        const x3: u32 = 0;
    """, """const '_ : i32 = '_;""")

    fun `test ConstantItem Expression`() = doTest("""
        const x1: i16 = 0;
        <warning descr="">const x2: i32 = 1;</warning>
        const x3: u32 = 2;
    """, """const '_ : '_ = 1;""")
}
