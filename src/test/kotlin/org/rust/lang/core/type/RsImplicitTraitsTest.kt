/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.CheckTestmarkHit
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode.TYPE
import org.rust.lang.core.psi.RsCodeFragmentFactory
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.TYPES
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.infer.TypeInferenceMarks
import org.rust.lang.core.types.ty.*
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

    fun `test str is not Sized`() = doTest("""
        fn foo() -> Box<str> { unimplemented!() }
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

    fun `test type parameter with qSized bound is not Sized`() = doTest("""
        fn foo<T: ?Sized>() -> Box<T> { unimplemented!() }
                                 //^ !Sized
    """)

    fun `test type parameter with qSized bound is not Sized 2`() = doTest("""
        fn foo<T>() -> Box<T> where T: ?Sized { unimplemented!() }
                         //^ !Sized
    """)

    fun `test type parameter with Sized bound on impl member function is Sized`() = doTest("""
        impl<T: ?Sized> Box<T> {
            fn foo() -> Box<T> where T: Sized { unimplemented!() }
        }                 //^ Sized
    """)

    fun `test type parameter with Sized bound on triat member function is Sized`() = doTest("""
        trait Tra<T: ?Sized> {
            fn foo() -> T where T: Sized { todo!() }
        }             //^ Sized
    """)

    fun `test Self is qSized by default`() = doTest("""
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

    fun `test Self is Sized if trait is Sized (vie where clause)`() = doTest("""
        trait Foo where Self: Sized {
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

    fun `test derive for generic type`() = doTest("""
        struct X; // Not `Copy`
        #[derive(Copy, Clone)]
        struct S<T>(T);
        type T = S<X>;
               //^ !Copy
    """)

    fun `test tuple of 'Copy' types is 'Copy'`() = doTest("""
        type T = ((), ());
               //^ Copy
    """)

    fun `test tuple of not 'Copy' types is not 'Copy'`() = doTest("""
        struct X;
        type T = (i32, X);
               //^ !Copy
    """)

    fun `test array of 'Copy' type is 'Copy'`() = doTest("""
        type T = [(); 4];
               //^ Copy
    """)

    fun `test array of non 'Copy' type is not 'Copy'`() = doTest("""
        struct X;
        type T = [X; 4];
               //^ !Copy
    """)

    fun `test invalid self-containing struct 1`() = doTest("""
        struct S {
            field: Self
        }
        type T = S;
               //^ Sized
    """)

    fun `test invalid self-containing struct 2`() = doTest("""
        struct Rc<T>(T);
        struct S {
            field: Rc<Self>
        }
        type T = S;
               //^ Sized
    """)

    @CheckTestmarkHit(TypeInferenceMarks.UnsizeArrayToSlice::class)
    fun `test unsize array to slice`() = doTest("""
        type T = [i32; 2];
               //^ Unsize<[i32]>
    """)

    fun `test don't unsize array to slice if element types mismatch`() = doTest("""
        type T = [i32; 2];
               //^ !Unsize<[u8]>
    """)

    @CheckTestmarkHit(TypeInferenceMarks.UnsizeToTraitObject::class)
    fun `test struct to trait object`() = doTest("""
        struct S;
        trait Foo {}
        impl Foo for S {}
        type T = S;
               //^ Unsize<dyn Foo>
    """)

    @CheckTestmarkHit(TypeInferenceMarks.UnsizeTuple::class)
    fun `test unsize tuple with unsize last field`() = doTest("""
        type T = (i32, [i32; 2]);
               //^ Unsize<(i32, [i32])>
    """)

    fun `test don't unsize tuple with different size`() = doTest("""
        type T = (i32, i32, [i32; 2]);
               //^ !Unsize<(i32, [i32])>
    """)

    fun `test don't unsize tuple with different types`() = doTest("""
        type T = (i32, [i32; 2]);
               //^ !Unsize<(u8, [i32])>
    """)

    @CheckTestmarkHit(TypeInferenceMarks.UnsizeStruct::class)
    fun `test unsize struct with unsize last field`() = doTest("""
        struct S<T: ?Sized> {
            head: i32,
            tail: T,
        }
        type T = S<[i32; 2]>;
               //^ Unsize<S<[i32]>>
    """)

    fun `test don't unsize struct with different types`() = doTest("""
        struct S<H, T: ?Sized> {
            head: H,
            tail: T,
        }
        type T = S<i32, [i32; 2]>;
               //^ !Unsize<u32, S<[i32]>>
    """)

    fun `test don't unsize struct with multiple fields affected`() = doTest("""
        struct S<T: ?Sized> {
            head: T,
            tail: T,
        }
        type T = S<[i32; 2]>;
               //^ !Unsize<S<[i32]>>
    """)

    private fun checkPrimitiveTypes(traitName: String) {
        val allIntegers = TyInteger.VALUES.toTypedArray()
        val allFloats = TyFloat.VALUES.toTypedArray()
        for (ty in listOf(TyBool, TyChar, TyUnit, TyNever, *allIntegers, *allFloats)) {
            doTest("""
                fn foo() -> $ty { unimplemented!() }
                          //^ $traitName
            """)
        }
    }

    private fun doTest(@Language("Rust") code: String) {
        val fullTestCode = """
            #[lang = "sized"]  pub trait Sized {}
            #[lang = "copy"]   pub trait Copy {}
            #[lang = "unsize"] pub trait Unsize<T: ?Sized> {}

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
        val traitPath = RsCodeFragmentFactory(project).createPath(traitName, myFixture.file as RsFile, TYPE, TYPES)
            ?: error("Cannot parse path `$traitName`")
        val trait = traitPath.reference?.advancedResolve()?.downcast<RsTraitItem>()
            ?: error("Cannot resolve path `traitName` to a trait")
        val hasImpl = lookup.canSelect(TraitRef(typeRef.type, trait))

        check(mustHaveImpl == hasImpl) {
            "Expected: `${typeRef.type}` ${if (mustHaveImpl) "has" else "doesn't have" } impl of `$traitName` trait"
        }
    }
}
