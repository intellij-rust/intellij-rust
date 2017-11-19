/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.ty.TyChar
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type

class RsImplicitTraitsTest : RsTypificationTestBase() {

    fun `test primitive types are Sized`() = checkPrimitiveTypes("Sized")

    fun `test array is Sized`() = doTest("""
        fn foo() -> [i32; 2] { unimplemented!() }
                  //^ Sized
    """)

    fun `test slice is not Sized`() = doTest("""
        fn foo() -> Box<[i32]> { unimplemented!() }
                      //^ !Sized
    """)

    fun `test trait object is not Sized`() = doTest("""
        trait Foo {}
        fn foo() -> Box<Foo> { unimplemented!() }
                       //^ !Sized
    """)

    fun `test enum is Sized`() = doTest("""
        enum FooBar { Foo, Bar }
        fn foo() -> FooBar { unimplemented!() }
                      //^ Sized
    """)

    fun `test struct is Sized`() = doTest("""
        struct Foo { foo: i32 }
        fn foo() -> Foo { unimplemented!() }
                    //^ Sized
    """)

    fun `test struct with DST field is not Sized`() = doTest("""
        struct Foo { foo: i32, bar: [i32] }
        fn foo() -> Box<Foo> { unimplemented!() }
                       //^ !Sized
    """)

    fun `test tuple struct is Sized`() = doTest("""
        struct Foo(i32);
        fn foo() -> Foo { unimplemented!() }
                    //^ Sized
    """)

    fun `test tuple struct with DST field is not Sized`() = doTest("""
        struct Foo(i32, [i32]);
        fn foo() -> Box<Foo> { unimplemented!() }
                       //^ !Sized
    """)

    fun `test empty struct is Sized`() = doTest("""
        struct Foo;
        fn foo() -> Foo { unimplemented!() }
                    //^ Sized
    """)

    fun `test tuple is Sized`() = doTest("""
        fn foo() -> (i32, bool) { unimplemented!() }
                  //^ Sized
    """)

    fun `test tuple with DST field is not Sized`() = doTest("""
        fn foo() -> Box<(i32, [i32])> { unimplemented!() }
                      //^ !Sized
    """)

    fun `test reference is Sized`() = doTest("""
        fn foo() -> &i32 { unimplemented!() }
                  //^ Sized
    """)

    fun `test pointer is Sized`() = doTest("""
        fn foo() -> *const u32 { unimplemented!() }
                   //^ Sized
    """)

    fun `test type parameter is Sized by default`() = doTest("""
        fn foo<T>() -> T { unimplemented!() }
                     //^ Sized
    """)

    fun `test type parameter with Sized bound is Sized`() = doTest("""
        fn foo<T: Sized>() -> T { unimplemented!() }
                            //^ Sized
    """)

    fun `test type parameter with ?Sized bound is not Sized`() = doTest("""
        fn foo<T: ?Sized>() -> Box<T> { unimplemented!() }
                                 //^ !Sized
    """)

    fun `test type parameter with ?Sized bound is not Sized 2`() = doTest("""
        fn foo<T>() -> Box<T> where T: ?Sized { unimplemented!() }
                         //^ !Sized
    """)

    fun `test Self is ?Sized by default`() = doTest("""
        trait Foo {
            fn foo(self: Self);
                        //^ !Sized
        }
    """)

    fun `test Self is Sized if trait is Sized`() = doTest("""
        trait Foo : Sized {
            fn foo(self: Self);
                        //^ Sized
        }
    """)

    fun `test Self is Sized in Sized type impl`() = doTest("""
        trait Foo {
            fn foo(self: Self);
        }
        struct Bar;
        impl Foo for Bar {
            fn foo(self: Self) { unimplemented!() }
                        //^ Sized
        }
    """)

    private fun checkPrimitiveTypes(traitName: String) {
        val allIntegers = TyInteger.Kind.values().map { TyInteger(it) }.toTypedArray()
        val allFloats = TyFloat.Kind.values().map { TyFloat(it) }.toTypedArray()
        for (ty in listOf(TyBool, TyChar, *allIntegers, *allFloats)) {
            doTest("""
                fn foo() -> $ty { unimplemented!() }
                          //^ $traitName
            """)
        }
    }

    private fun doTest(@Language("Rust") code: String) {
        val fullTestCode = """
            #[lang = "sized"]
            pub trait Sized {}

            $code
        """

        InlineFile(fullTestCode)

        val (typeRef, data) = findElementAndDataInEditor<RsTypeReference>()
        val (traitName, mustHaveImpl) = if (data.startsWith('!')) {
            data.drop(1) to false
        } else {
            data to true
        }

        val lookup = ImplLookup.relativeTo(typeRef)
        val hasImpl = lookup.isSized(typeRef.type)

        check(mustHaveImpl == hasImpl) {
            "Expected: `${typeRef.type}` ${if (mustHaveImpl) "has" else "doesn't have" } impl of `$traitName` trait"
        }
    }
}
