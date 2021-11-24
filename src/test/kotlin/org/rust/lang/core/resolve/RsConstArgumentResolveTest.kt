/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsConstArgumentResolveTest : RsResolveTestBase() {
    // Type path arguments:

    fun `test disambiguate type argument`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        struct Foo<T>(T);
        type T = Foo<A>;
                   //^
    """)

    fun `test disambiguate type argument when parent path is unresolved`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        type T = Unknown<A>;
                       //^
    """)

    fun `test resolve const parameter that looks like a type parameter`() = checkByCode("""
        const A: i32 = 0;
            //X
        struct Foo<const S: i32>();
        type T = Foo<A>;
                   //^
    """)

    fun `test in the case of ambiguity const argument is resolved to a type`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        struct Foo<const S: i32>();
        type T = Foo<A>;
                   //^
    """)

    // Expression path arguments:

    fun `test disambiguate type argument in expr path`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        fn foo<T>() {}
        fn main() {
            foo::<A>();
                //^
        }
    """)

    fun `test disambiguate type argument in expr path when parent path is unresolved`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        fn main() {
            unknown::<A>();
                    //^
        }
    """)

    fun `test resolve const parameter that looks like a type parameter in expr path`() = checkByCode("""
        const A: i32 = 0;
            //X
        fn foo<const S: i32>() {}
        fn main() {
            foo::<A>();
                //^
        }
    """)

    fun `test in the case of ambiguity const argument is resolved to a type in expr path`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        fn foo<const S: i32>() {}
        fn main() {
            foo::<A>();
                //^
        }
    """)

    fun `test type is preferred in an upper scope`() = checkByCode("""
        struct N { f: i32 }
             //X
        fn foo<const N: i32>() {
            foo::<N>();
        }       //^
    """)

    fun `test type is preferred in an upper scope (unit struct)`() = checkByCode("""
        struct N;
             //X
        fn bar<T>() {}
        fn foo<const N: i32>() {
            bar::<N>();
        }       //^
    """)

    // Method call arguments:

    fun `test disambiguate type argument in method call`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        struct S;
        impl S {
            fn foo<const S: i32>(&self) {}
        }
        fn main() {
            S.foo::<A>();
                  //^
        }
    """)

    fun `test disambiguate type argument in method call when parent path is unresolved`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        struct S;
        fn main() {
            S.unknown::<A>();
                      //^
        }
    """)

    fun `test resolve const parameter that looks like a type parameter in method call`() = checkByCode("""
        const A: i32 = 0;
            //X
        struct S;
        impl S {
            fn foo<const S: i32>(&self) {}
        }
        fn main() {
            s.foo::<A>();
                  //^
        }
    """)

    fun `test in the case of ambiguity const argument is resolved to a type in method call`() = checkByCode("""
        const A: i32 = 0;
        type A = i32;
           //X
        struct S;
        impl S {
            fn foo<const S: i32>(&self) {}
        }
        fn main() {
            s.foo::<A>();
                  //^
        }
    """)
}
