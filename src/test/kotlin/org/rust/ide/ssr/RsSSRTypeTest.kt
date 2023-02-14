/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

class RsSSRTypeTest : RsSSRTestBase() {
    fun `test Path`() = doTest("""
        mod foo { pub struct Foo; }
        mod bar { pub struct Foo; }
        /*warning*/struct S1(Foo);/*warning**/
        /*warning*/struct S2(foo::Foo);/*warning**/
        /*warning*/struct S4(bar::Foo);/*warning**/
        struct S5(Bar);
    """, """struct '_(Foo);""")

//    fun `test Path fully qualified`() = doTest("""
//        mod foo { pub struct Foo; }
//        mod bar { pub struct Foo; }
//        use foo::Foo;
//        /*warning*/struct S1(Foo);/*warning**/
//        /*warning*/struct S2(foo::Foo);/*warning**/
//        /*warning*/struct S3(crate::foo::Foo);/*warning**/
//        struct S4(bar::Foo);
//        struct S5(Bar);
//        mod baz {
//            use crate::bar::Foo;
//            struct S6(Foo);
//        }
//    """, """struct '_(crate::foo::Foo);""")

//    fun `test Path fully qualified text filter`() = doTest("""
//        mod foo { pub struct Foo; }
//        mod bar { pub struct Foo; }
//        mod baz { pub struct Foo; }
//        use foo::Foo;
//        struct S1(Foo);
//        struct S2(foo::Foo);
//        struct S3(crate::foo::Foo);
//        /*warning*/struct S4(bar::Foo);/*warning**/
//        struct S5(Bar);
//        mod test1 {
//            use crate::bar::Foo;
//            /*warning*/struct S6(Foo);/*warning**/
//        }
//        mod test2 {
//            use crate::baz::Foo;
//            /*warning*/struct S7(Foo);/*warning**/
//        }
//    """, """struct '_(crate::'_:[regex(b..)]::Foo);""")

    fun `test Path RefLikeType`() = doTest("""
        /*warning*/struct S1(&A);/*warning**/
        struct S2(A);
        struct S3(&mut A);
    """, """struct '_(&A);""")

    fun `test Path RefLikeType refref`() = doTest("""
        /*warning*/struct S1(&&A);/*warning**/
        struct S2(&A);
        struct S3(A);
        struct S4(&mut A);
    """, """struct '_(&&A);""")

    fun `test Path RefLikeType mut`() = doTest("""
        /*warning*/struct S1(&mut A);/*warning**/
        struct S2(A);
        struct S3(&A);
    """, """struct '_(&mut A);""")

    fun `test Path RefLikeType star mut`() = doTest("""
        /*warning*/struct S1(*mut A);/*warning**/
        struct S2(&mut A);
        struct S3(&*mut A);
        struct S4(A);
        struct S5(&A);
    """, """struct '_(*mut A);""")

    fun `test Path RsLifetime QUOTE_IDENTIFIER`() = doTest("""
        /*warning*/struct S1<'a>(&'a u32);/*warning**/
        /*warning*/struct S2<'b>(&'b u32);/*warning**/
        struct S3(&u32);
    """, """struct '_(&\''_ u32)""")

    fun `test Path RsTypeArgumentList`() = doTest("""
        /*warning*/struct S1(x<T1>);/*warning**/
        struct S2(x);
        struct S3(x<T2>);
        struct S4(T1);
    """, """struct '_(x<T1>);""")

    fun `test Path RsTypeArgumentList with 2 entries`() = doTest("""
        /*warning*/struct S1(X<u8, u8>);/*warning**/
        /*warning*/struct S2(X<String, u8>);/*warning**/
        struct S3(X<u8, u16>);
        struct S4(X<u8>);
    """, """struct '_(X<'_, u8>);""")

    fun `test Path RsTypeArgumentList with 3 or 4 entries`() = doTest("""
        /*warning*/struct S1(X<A1, B1, B2, A2>);/*warning**/
        /*warning*/struct S2(X<A1, B1, A2>);/*warning**/
        struct S3(X<A1, B1, B2, B3, A2>);
        struct S4(X<A1, A2>);
    """, """struct '_(X<A1, '_{1,2}, A2>);""")

    fun `test Path RsTypeParameterList`() = doTest("""
        /*warning*/struct S1<T: T1 + T2 + 'static, U: Into<T3>>(u8);/*warning**/
        /*warning*/struct S2<U: Into<T3>, T: T1 + T2 + 'static>(u8);/*warning**/
        struct S3<'a, T: T1 + T2 + 'a, U: Into<T3>>(u8);
        struct S4<T: T1 + T2, U: Into<T3>>(u8);
        struct S5<T: T1 + 'static, U: Into<T3>>(u8);
        struct S6<T: T1 + T2 + 'static>(u8);
        struct S7<T, U>(u8);
        struct S8<T: T1 + T2 + 'static, U: Into>(u8);
        struct S9<U: Into<T3>>(u8);
        struct S10(u8);
    """, """struct '_<T: T1 + T2 + \'static, U: Into<T3>>('_);""")

    fun `test Path RsWherePred`() = doTest("""
        /*warning*/struct S1<'a, T> where T: 'a {}/*warning**/
        struct S2<'b, T> where T: 'b {}
        struct S3<'a> where T: 'a {}
        struct S4<'b, U> where U: 'b {}
        struct S5<'a, T> where U: 'a {}
    """, """struct '_<\'a, T> where T: \'a {}""")

    fun `test Path RsConstParameter`() = doTest("""
        /*warning*/struct S1<X, const Y: usize>([X; Y]);/*warning**/
        struct S2<X, const Z: usize>([X; Y]);
        struct S3<X, const Y: usize>([X; Z]);
        struct S4<X, const Y: usize>([A; Y]);
        struct S5<X, const Y: usize>([X; A]);
    """, """struct '_<X, const Y: usize>([X; Y])""")

//    fun `test Path RsUseItem`() = doTest("""
//        mod mod1 {
//            mod inner {
//                pub struct Foo;
//                /*warning*/const C: Foo = Foo;/*warning**/
//            }
//            pub use inner::Foo as Bar;
//        }
//    """, """const '_ : crate::mod1::Bar;""")
}
