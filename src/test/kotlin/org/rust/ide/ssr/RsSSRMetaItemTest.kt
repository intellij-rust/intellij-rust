/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

class RsSSRMetaItemTest : RsSSRTestBase() {
    fun `test InnerAttribute IDENTIFIER`() = doTest("""
        /*warning*/#![id]/*warning**/
        /*warning*/#![id(lit1)]/*warning**/
        /*warning*/#![id = "lit1"]/*warning**/
        #![id2(id)]
    """, """#![id]""")

    fun `test InnerAttribute IDENTIFIER = LITERAL`() = doTest("""
        #![id]
        /*warning*/#![id = "lit1"]/*warning**/
        #![id(lit1)]
        #![id = "lit2"]
    """, """#![id = "lit1"]""")

    fun `test InnerAttribute IDENTIFIER ( LITERAL )`() = doTest("""
        #![id()]
        /*warning*/#![id(y1)]/*warning**/
        /*warning*/#![id(y1, y2)]/*warning**/
        /*warning*/#![id(y2, y1)]/*warning**/
        #![id(y2)]
        #![id2(y1)]
        #![id()]
        #![id = "y1"]
    """, """#![id(y1)]""")

    fun `test InnerAttribute IDENTIFIER ( 1 LITERAL )`() = doTest("""
        #![id()]
        /*warning*/#![id(lit1)]/*warning**/
        #![id(lit2, lit1)]
    """, """#![id(lit1, '_{0,0})]""")

    fun `test InnerAttribute IDENTIFIER ( )`() = doTest("""
        /*warning*/#![id()]/*warning**/
        /*warning*/#![id(y1)]/*warning**/
        /*warning*/#![id(y1, y2)]/*warning**/
        #![id2(y1)]
        #![id = "y1"]
    """, """#![id()]""")

    fun `test InnerAttribute IDENTIFIER ( MetaItem )`() = doTest("""
        #![id()]
        #![id(y1)]
        /*warning*/#![id(y1(z1))]/*warning**/
        /*warning*/#![id(y1(z1), y2)]/*warning**/
        #![id(y1(z2))]
    """, """#![id(y1(z1))]""")

    fun `test InnerAttribute IDENTIFIER ( MetaSeq , MetaItem )`() = doTest("""
        #![id()]
        #![id(y1)]
        #![id(y1, y2)]
        /*warning*/#![id(y1, y2 = "z1")]/*warning**/
        /*warning*/#![id(y2 = "z1", y3(), y1)]/*warning**/
        #![id2(y1)]
        #![id = "y1"]
    """, """#![id(y1, y2 = "z1")]""")

    fun `test OuterAttribute`() = doTest("""
        /*warning*/#[id(y1 = "z1")]
        struct A;/*warning**/
        /*warning*/#[id(y2, y1 = "z1")]
        struct B;/*warning**/
        #[id(y3)]
        struct C;
        #[id(y1(z2))]
        struct D;
        #[id(y1 = "z2")]
        struct E;
        struct F;
    """, """#[id(y1 = "z1")] struct '_""")
}
