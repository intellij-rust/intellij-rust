/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.needsDrop
import org.rust.lang.core.types.rawType
import org.rust.lang.utils.evaluation.ThreeValuedLogic

class RsNeedsDropTest : RsTypificationTestBase() {

    fun `test primitive type doesn't need drop`() = doTest("""
        type T = usize;
               //^ !needs_drop
    """)

    fun `test doesn't need drop when struct doesn't have fields`() = doTest("""
        struct S;
        type T = S;
               //^ !needs_drop
    """)

    fun `test needs drop when struct's fields need drop`() = doTest("""
        struct S {
            a: Droppable
        }
        type T = S;
               //^ needs_drop
    """)

    fun `test doesn't need drop when struct's fields don't need drop`() = doTest("""
        struct S {
            a: u32
        }
        type T = S;
               //^ !needs_drop
    """)

    fun `test needs drop when impl Drop trait`() = doTest("""
        type T = Droppable;
               //^ needs_drop
    """)

    fun `test struct needs drop when generic param needs drop`() = doTest("""
        struct GenericStruct<T> {
            a: i32,
            b: T
        }
        type A = GenericStruct<Droppable>;
               //^ needs_drop
    """)

    fun `test struct doesn't need drop when generic param doesn't need drop`() = doTest("""
        struct GenericStruct<T> {
            a: i32,
            b: T
        }
        type B = GenericStruct<f32>;
               //^ !needs_drop
    """)

    fun `test doesn't need drop when type that needs drop is wrapped in ManuallyDrop`() = doTest("""
        #[lang = "manually_drop"]
        pub struct ManuallyDrop<T: ?Sized> {
            value: T,
        }
        struct S {
            a: Droppable
        }
        type B = ManuallyDrop<S>;
               //^ !needs_drop
    """)

    fun `test tuple needs drop when contains type that needs drop`() = doTest("""
        type B = (Droppable, u32);
               //^ needs_drop
    """)

    fun `test tuple doesn't need drop when contains only types that doesn't need drop`() = doTest("""
        type B = (i8, u32);
               //^ !needs_drop
    """)

    fun `test array needs drop when contains type that needs drop`() = doTest("""
        type B = [Droppable; 2];
               //^ needs_drop
    """)

    fun `test array doesn't need drop when contains type that doesn't need drop`() = doTest("""
        type B = [u32; 2];
               //^ !needs_drop
    """)

    fun `test enum needs drop when any enum value contains type that needs drop`() = doTest("""
        enum S {
            Foo(Droppable),
            Bar
        }

        type B = S;
               //^ needs_drop
    """)

    fun `test enum doesn't need drop when all of enum values don't need drop`() = doTest("""
        enum S {
            Foo,
            Bar
        }

        type B = S;
               //^ !needs_drop
    """)

    fun `test enum needs drop when generic param needs drop`() = doTest("""
        enum GenericEnum<Q, W, E> {
            Z(Q), X(W), C(E)
        }

        type B = GenericEnum<Droppable, u8, i32>;
               //^ needs_drop
    """)

    fun `test unknown for unknown type`() = doTest("""
        type B = UnknownType;
               //^ ?needs_drop
    """)

    fun `test unions cannot contain fields that may need dropping thus they don't need drop`() = doTest("""
        union A {
            f1: i32,
            f2: String
        }
        type B = A;
               //^ !needs_drop
    """)

    fun `test references doesn't need drop`() = doTest("""
        type B = &[Droppable];
               //^ !needs_drop
    """)

    fun `test pointer doesn't need drop`() = doTest("""
        type B = *const Droppable;
               //^ !needs_drop
    """)

    fun `test function doesn't need drop`() = doTest("""
        type B = fn() -> i32;
               //^ !needs_drop
    """)

    fun `test dyn needs drop`() = doTest("""
        trait T {}
        type B = dyn T;
               //^ needs_drop
    """)

    fun `test impl needs drop`() = doTest("""
        trait T {}

        fn foo(bar: impl T) {}
                  //^ needs_drop
    """)

    fun `test type parameter unknown if needs drop`() = doTest("""
        struct S<T> { f1: T }
                        //^ ?needs_drop
    """)

    fun `test recursion limit`() = doTest("""
        struct B {
            f1: u32
        }

        struct A {
            f1: B
        }

        type T = A;
               //^ needs_drop
    """, """
        #![recursion_limit = "1"]
    """)

    fun `test tuple struct needs drop when its fields need drop`() = doTest("""
        struct B(Droppable);

        type T = B;
               //^ needs_drop
    """)

    fun `test projection type`() = doTest("""
        trait Trait { type Item; }
        fn foo<T: Trait>() -> T::Item { unimplemented!() }
                            //^ ?needs_drop
    """)

    fun `test infer type`() = doTest("""
        fn foo<T>(a: T) -> T {
            a
        }

        fn bar() {
            let b = foo::<_>(8);
                        //^ ?needs_drop
        }
    """)

    fun `test enum with unknown field`() = doTest("""
        enum E {
            A(Unknown)
        }

        type T = E;
               //^ ?needs_drop
    """)

    fun `test struct with unknown field`() = doTest("""
        struct S {
            f1: (Unknown)
        }

        type T = E;
               //^ ?needs_drop
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Rust") beforeCode: String = "") {
        val fullTestCode = """
            $beforeCode

            #[lang = "copy"]   pub trait Copy {}
            #[lang = "drop"]   pub trait Drop {}

            struct Droppable;
            impl Drop for Droppable {}

            $code
        """

        InlineFile(fullTestCode)

        val (typeRef, data) = findElementAndDataInEditor<RsTypeReference>()
        val dataNeedsDrop = when (data) {
            "needs_drop" -> ThreeValuedLogic.True
            "!needs_drop" -> ThreeValuedLogic.False
            "?needs_drop" -> ThreeValuedLogic.Unknown
            else -> error("Unknown marker: `$data`")
        }

        val lookup = ImplLookup.relativeTo(typeRef)
        val needsDrop = lookup.needsDrop(typeRef.rawType, typeRef)

        check(dataNeedsDrop == needsDrop) {
            when(dataNeedsDrop) {
                ThreeValuedLogic.True -> "Type needs drop"
                ThreeValuedLogic.False -> "Type does not need drop"
                ThreeValuedLogic.Unknown -> "Unknown if type needs drop"
            } + " but was $needsDrop"
        }
    }
}
