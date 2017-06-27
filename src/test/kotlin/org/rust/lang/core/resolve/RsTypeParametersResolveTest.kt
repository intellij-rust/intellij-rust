/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsTypeParametersResolveTest : RsResolveTestBase() {
    fun testFn() = checkByCode("""
        fn foo<T>() -> T {
             //X
            let x: T = unimplemented!();
                 //^
            }
    """)

    fun testImplMethod() = checkByCode("""
        struct S;

        impl S {
            fn foo<Param>(
                    //X
                param: Param
            ) {}      //^
        }
    """)

    fun testTraitMethod() = checkByCode("""
        trait T {
            fn f<Param>()
                //X
                -> Param;
                    //^
        }
    """)

    fun testStruct() = checkByCode("""
        struct S<Thing> {
                //X
            field: Vec<Thing>
                      //^
        }
    """)

    fun testEnum() = checkByCode("""
        enum E<T> {
             //X
            V(T)
            //^
        }
    """)

    fun testTrait() = checkByCode("""
        trait T<Param> {
                //X
            fn new() -> Param;
                        //^
        }
    """)

    fun testImpl() = checkByCode("""
        struct S<T> { field: T }

        impl<T> S<T> {
           //X
            fn foo() -> T { }
                      //^
        }
    """)

    fun testTypeAlias() = checkByCode("""
        use std::result;

        pub type Result<T> =
                      //X
            result::Result<T, Error>;
                         //^
    """)

    fun testNoLeakInEnum() = checkByCode("""
        enum E<T> { X }

        fn main() { let _ = E::T; }
                             //^ unresolved
    """)

    fun testNoLeakInStruct() = checkByCode("""
        struct S<T>;

        fn main() { let _: S::T = unreachable!(); }
                            //^ unresolved
    """)
}
