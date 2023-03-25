/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

class RsSSRStructTest : RsSSRTestBase() {
    fun `test empty struct`() = doTest("""
        struct S;
        /*warning*/struct SS;/*warning**/
        /*warning*/struct SSS;/*warning**/
        union UU;
    """, "struct '_:[regex( \\w{2,3} )]")

    fun `test struct block fields`() = doTest("""
        /*warning*/struct S1 {
            a: u32,
            b: i32
        }/*warning**/

        struct S2 {
            b: u32
        }

        struct S3 {
            a: i32
        }
    """, """
    struct '_ {
        '_\: u32,
        '_\: i32
    }""")

    fun `test struct block fields visibility`() = doTest("""
        /*warning*/struct S1 { pub a: u32 }/*warning**/
        struct S2 { a: u32 }
    """, """
    struct '_ {
        pub '_\: u32
    }""")

    fun `test struct block fields attributes`() = doTest("""
        /*warning*/struct S1 { #[foo] a: u32 }/*warning**/
        /*warning*/struct S2 { #[a1] #[foo] #[a2] a: u32 }/*warning**/
        struct S3 { a: u32 }
    """, """
    struct '_ {
        #[foo] '_\: '_
    }""")

    fun `test struct tuple field`() = doTest("""
        /*warning*/struct S1(u32);/*warning**/
        struct S2(i32);
    """, "struct '_(u32)"
    )

    fun `test struct tuple field visibility`() = doTest("""
        struct S1(u32);
        /*warning*/struct S2(pub i32);/*warning**/
        /*warning*/struct S3(pub u32);/*warning**/
    """, "struct '_(pub '_)"
    )

    fun `test struct tuple field count filter`() = doTest("""
        struct S1(u32);
        /*warning*/struct S2(i32, u32);/*warning**/
        /*warning*/struct S3(i32, u32, bool);/*warning**/
        struct S4(i32, u32, bool, bool);
    """, "struct '_('_{2,3})"
    )

    fun `test struct generic parameters`() = doTest("""
        /*warning*/struct S1<T1>(u32);/*warning**/
        struct S2<T>(u32);
        struct S3(u32);
    """, """struct '_<'_:[regex( \w{2,3} )]>(u32)""")


    fun `test union`() = doTest("""
        /*warning*/union U1 { pub a: u32, pub b: i32 }/*warning**/
        struct S1 { pub a: u32, pub b: i32 }
    """, """
    union '_ {
        '_\: u32,
        '_\: i32,
    }""")
}
