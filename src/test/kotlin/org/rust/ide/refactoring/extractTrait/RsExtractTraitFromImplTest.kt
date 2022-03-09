/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractTrait

class RsExtractTraitTest : RsExtractTraitBaseTest() {

    fun `test one method 1`() = doTest("""
        struct S;
        impl S {
            /*caret*/fn a(&self) {}
        }
    """, """
        struct S;

        impl Trait for S {
            fn a(&self) {}
        }

        trait Trait {
            fn a(&self);
        }
    """)

    fun `test one method 2`() = doTest("""
        struct S;
        impl S {
            /*caret*/fn a(&self) {}
            fn b(&self) {}
        }
    """, """
        struct S;
        impl S {
            fn b(&self) {}
        }

        impl Trait for S {
            fn a(&self) {}
        }

        trait Trait {
            fn a(&self);
        }
    """)

    fun `test two methods`() = doTest("""
        struct S;
        impl S {
            /*caret*/fn a(&self) {}
            /*caret*/fn b(&self) {}
        }
    """, """
        struct S;

        impl Trait for S {
            fn a(&self) {}
            fn b(&self) {}
        }

        trait Trait {
            fn a(&self);
            fn b(&self);
        }
    """)

    fun `test public method`() = doTest("""
        struct S;
        impl S {
            /*caret*/pub fn a(&self) {}
        }
    """, """
        struct S;

        impl Trait for S {
            fn a(&self) {}
        }

        trait Trait {
            fn a(&self);
        }
    """)

    fun `test associated constant`() = doTest("""
        struct S;
        impl S {
            /*caret*/const C: i32 = 0;
        }
    """, """
        struct S;

        impl Trait for S {
            const C: i32 = 0;
        }

        trait Trait {
            const C: i32;
        }
    """)

    fun `test associated type`() = doTest("""
        struct S;
        impl S {
            /*caret*/type T = i32;
        }
    """, """
        struct S;

        impl Trait for S {
            type T = i32;
        }

        trait Trait {
            type T;
        }
    """)

    fun `test generics 1`() = doTest("""
        struct S<A> { a: A }
        impl<A> S<A> {
            /*caret*/fn get_a(&self) -> &A { &self.a }
        }
    """, """
        struct S<A> { a: A }

        impl<A> Trait<A> for S<A> {
            fn get_a(&self) -> &A { &self.a }
        }

        trait Trait<A> {
            fn get_a(&self) -> &A;
        }
    """)

    fun `test generics 2`() = doTest("""
        struct S<A> { a: A }
        impl<A> S<A> {
            fn get_a(&self) -> &A { &self.a }
            /*caret*/fn b(&self) {}
        }
    """, """
        struct S<A> { a: A }
        impl<A> S<A> {
            fn get_a(&self) -> &A { &self.a }
        }

        impl<A> Trait for S<A> {
            fn b(&self) {}
        }

        trait Trait {
            fn b(&self);
        }
    """)

    fun `test generics complex`() = doTest("""
        struct S<A, B> { a: A, b: B }
        impl<A, B> S<A, B> where A: BoundA, B: BoundB {
            /*caret*/fn get_a(&self) -> &A { &self.a }
            fn get_b(&self) -> &B { &self.b }
        }
    """, """
        struct S<A, B> { a: A, b: B }
        impl<A, B> S<A, B> where A: BoundA, B: BoundB {
            fn get_b(&self) -> &B { &self.b }
        }

        impl<A, B> Trait<A> for S<A, B> where A: BoundA, B: BoundB {
            fn get_a(&self) -> &A { &self.a }
        }

        trait Trait<A> where A: BoundA {
            fn get_a(&self) -> &A;
        }
    """)

    fun `test generics in associated type`() = doTest("""
        struct S<A> { a: A }
        impl<A> S<A> {
            /*caret*/type T = A;
        }
    """, """
        struct S<A> { a: A }

        impl<A> Trait for S<A> {
            type T = A;
        }

        trait Trait {
            type T;
        }
    """)

    fun `test generics with bounds`() = doTest("""
        struct S<A> { a: A }
        impl<A: Debug> S<A> {
            /*caret*/fn get_a(&self) -> &A { &self.a }
        }
    """, """
        struct S<A> { a: A }

        impl<A: Debug> Trait<A> for S<A> {
            fn get_a(&self) -> &A { &self.a }
        }

        trait Trait<A: Debug> {
            fn get_a(&self) -> &A;
        }
    """)

    fun `test generics in function 1`() = doTest("""
        struct S<A> { a: A }
        impl<A> S<A> {
            /*caret*/fn func(&self) {
                A::C;
            }
        }
    """, """
        struct S<A> { a: A }

        impl<A> Trait for S<A> {
            fn func(&self) {
                A::C;
            }
        }

        trait Trait {
            fn func(&self);
        }
    """)

    fun `test generics in function 2`() = doTest("""
        struct S<A> { a: A }
        impl<A> S<A> {
            /*caret*/fn func(&self, a: A) {}
        }
    """, """
        struct S<A> { a: A }

        impl<A> Trait<A> for S<A> {
            fn func(&self, a: A) {}
        }

        trait Trait<A> {
            fn func(&self, a: A);
        }
    """)

    fun `test generics in function 3`() = doTest("""
        struct S<A> { a: A }
        impl<A> S<A> {
            /*caret*/fn func(&self) -> A { unimplemented!() }
        }
    """, """
        struct S<A> { a: A }

        impl<A> Trait<A> for S<A> {
            fn func(&self) -> A { unimplemented!() }
        }

        trait Trait<A> {
            fn func(&self) -> A;
        }
    """)

    fun `test not available for trait impl`() = doUnavailableTest("""
        struct S;
        impl T for S {
            /*caret*/fn a(&self) {}
        }
    """)

    fun `test not available if no members`() = doUnavailableTest("""
        struct S;
        impl T for S { /*caret*/ }
    """)

    fun `test impl with attributes`() = doTest("""
        struct S;
        #[cfg(attr1)]
        impl S {
            #![cfg(attr2)]
            /*caret*/fn a(&self) {}
        }
    """, """
        struct S;

        #[cfg(attr1)]
        impl Trait for S {
            #![cfg(attr2)]
            fn a(&self) {}
        }

        #[cfg(attr1)]
        trait Trait {
            #![cfg(attr2)]
            fn a(&self);
        }
    """)

    fun `test copy doc comments of methods`() = doTest("""
        struct S;
        /// 1
        impl S {
            /// 2
            /*caret*/fn a(&self) {}
            /// 3
            fn b(&self) {}
        }
    """, """
        struct S;
        /// 1
        impl S {
            /// 3
            fn b(&self) {}
        }

        impl Trait for S {
            /// 2
            fn a(&self) {}
        }

        trait Trait {
            /// 2
            fn a(&self);
        }
    """)

    fun `test add trait imports`() = doTest("""
        struct Foo {}
        impl Foo {
            /*caret*/const ASSOC_CONST: i32 = 7;
            /*caret*/fn foo() {}
            /*caret*/fn bar(&self) {}
        }

        mod mod1 {
            fn func(foo: crate::Foo) {
                foo.bar();
                foo.bar();
            }
        }
        mod mod2 {
            fn func() {
                crate::Foo::foo();
            }
        }
        mod mod3 {
            fn func() {
                crate::Foo::ASSOC_CONST;
            }
        }
    """, """
        struct Foo {}

        impl Trait for Foo {
            const ASSOC_CONST: i32 = 7;
            fn foo() {}
            fn bar(&self) {}
        }

        trait Trait {
            const ASSOC_CONST: i32;
            fn foo();
            fn bar(&self);
        }

        mod mod1 {
            use crate::Trait;

            fn func(foo: crate::Foo) {
                foo.bar();
                foo.bar();
            }
        }
        mod mod2 {
            use crate::Trait;

            fn func() {
                crate::Foo::foo();
            }
        }
        mod mod3 {
            use crate::Trait;

            fn func() {
                crate::Foo::ASSOC_CONST;
            }
        }
    """)
}
