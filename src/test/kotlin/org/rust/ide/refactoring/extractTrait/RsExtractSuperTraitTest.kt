/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractTrait

class RsExtractSuperTraitTest : RsExtractTraitBaseTest() {

    fun `test one method 1`() = doTest("""
        trait Derived {
            /*caret*/fn a(&self);
        }
    """, """
        trait Derived: Trait {}

        trait Trait {
            fn a(&self);
        }
    """)

    fun `test one method 2`() = doTest("""
        trait Derived {
            /*caret*/fn a(&self);
            fn b(&self);
        }
    """, """
        trait Derived: Trait {
            fn b(&self);
        }

        trait Trait {
            fn a(&self);
        }
    """)

    fun `test two methods`() = doTest("""
        trait Derived {
            /*caret*/fn a(&self);
            /*caret*/fn b(&self);
        }
    """, """
        trait Derived: Trait {}

        trait Trait {
            fn a(&self);
            fn b(&self);
        }
    """)

    fun `test public trait`() = doTest("""
        pub trait Derived {
            /*caret*/fn a(&self);
        }
    """, """
        pub trait Derived: Trait {}

        pub trait Trait {
            fn a(&self);
        }
    """)

    fun `test restricted trait 1`() = doTest("""
        pub(crate) trait Derived {
            /*caret*/fn a(&self);
        }
    """, """
        pub(crate) trait Derived: Trait {}

        pub(crate) trait Trait {
            fn a(&self);
        }
    """)

    fun `test restricted trait 2`() = doTest("""
        mod inner1 {
            mod inner2 {
                pub(super) trait Derived {
                    /*caret*/fn a(&self);
                }
            }
        }
    """, """
        mod inner1 {
            mod inner2 {
                pub(super) trait Derived: Trait {}

                pub(in crate::inner1) trait Trait {
                    fn a(&self);
                }
            }
        }
    """)

    fun `test impl in same mod`() = doTest("""
        trait Derived {
            /*caret*/fn a(&self);
        }
        struct Struct;
        impl Derived for Struct {
            fn a(&self) { /* a */ }
        }
    """, """
        trait Derived: Trait {}

        trait Trait {
            fn a(&self);
        }

        struct Struct;
        impl Derived for Struct {}

        impl Trait for Struct {
            fn a(&self) { /* a */ }
        }
    """)

    fun `test impl in different mod`() = doTest("""
        trait Derived {
            /*caret*/fn a(&self);
        }
        mod inner {
            use crate::Derived;
            struct Struct;
            impl Derived for Struct {
                fn a(&self) { /* a */ }
            }
        }
    """, """
        trait Derived: Trait {}

        trait Trait {
            fn a(&self);
        }

        mod inner {
            use crate::{Derived, Trait};
            struct Struct;
            impl Derived for Struct {}

            impl Trait for Struct {
                fn a(&self) { /* a */ }
            }
        }
    """)

    fun `test add trait imports`() = doTest("""
        trait Derived {
            /*caret*/fn a(&self);
        }
        struct Struct;
        impl Derived for Struct {
            fn a(&self) { /* a */ }
        }

        mod inner {
            use crate::Derived;
            fn func(foo: crate::Struct) {
                foo.a();
            }
        }
    """, """
        trait Derived: Trait {}

        trait Trait {
            fn a(&self);
        }

        struct Struct;
        impl Derived for Struct {}

        impl Trait for Struct {
            fn a(&self) { /* a */ }
        }

        mod inner {
            use crate::{Derived, Trait};
            fn func(foo: crate::Struct) {
                foo.a();
            }
        }
    """)

    fun `test generics`() = doUnavailableTest("""
        trait Derived<A, B> {
            /*caret*/fn a(&self, _: A);
            fn b(&self, _: B);
        }
        struct Struct<C> {}
        impl<A1, A2, B, C> Derived<(A1, A2), B> for Struct<C> {
            fn a(&self, _: (A1, A2)) { /* a */ }
            fn b(&self, _: B) { /* b */ }
        }
    """)
}
