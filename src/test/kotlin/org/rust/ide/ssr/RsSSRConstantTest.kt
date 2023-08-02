/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

class RsSSRConstantTest : RsSSRTestBase() {
    fun `test ConstantItem IDENTIFIER`() = doTest("""
        const X1: i16 = 0;
        /*warning*/const X2: i32 = 0;/*warning**/
        const X3: u32 = 0;
    """, """const X2 : '_ = '_;""")

    fun `test ConstantItem Type`() = doTest("""
        const X1: i16 = 0;
        /*warning*/const X2: i32 = 0;/*warning**/
        const X3: u32 = 0;
    """, """const '_ : i32 = '_;""")

    fun `test ConstantItem Expression`() = doTest("""
        const X1: i16 = 0;
        /*warning*/const X2: i32 = 1;/*warning**/
        const X3: u32 = 2;
    """, """const '_ : '_ = 1;""")

    fun `test ConstantItem underscore`() = doTest("""
        /*warning*/const _: i32 = 0;/*warning**/
        const X: i32 = 0;
    """, """const _: i32 = 0;""")

    fun `test ConstantItem visibility`() = doTest("""
        /*warning*/pub const X1: i32 = 0;/*warning**/
        const X2: i32 = 0;
    """, """pub const '_""")

    fun `test ConstantItem outerAttr`() = doTest("""
        /*warning*/#[attr] const X1: i32 = 0;/*warning**/
        const X2: i32 = 0;
    """, """#[attr] const '_""")

    fun `test ConstantItem static`() = doTest("""
        /*warning*/static X1: i32 = 0;/*warning**/
        const X2: i32 = 0;
    """, """static '_""")

    fun `test ConstantItem static mut`() = doTest("""
        /*warning*/static mut X1: i32 = 0;/*warning**/
        static X2: i32 = 0;
    """, """static mut '_""")
}
