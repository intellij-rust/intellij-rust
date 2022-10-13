/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import junit.framework.ComparisonFailure

class RsInlineTypeAliasTest : RsInlineTestBase() {

    fun `test simple alias, base type as base type`() = doTest("""
        type Foo/*caret*/ = i32;
        fn func() {
            let _: Foo;
        }
    """, """
        fn func() {
            let _: i32;
        }
    """)

    fun `test simple alias, base type as path`() = doTest("""
        type Foo/*caret*/ = i32;
        fn func() {
            Foo::new();
        }
    """, """
        fn func() {
            i32::new();
        }
    """)

    fun `test simple alias, base type as path (struct literal)`() = doTest("""
        struct Struct {}
        type Foo/*caret*/ = Struct;
        fn func() {
            let _ = Foo {};
        }
    """, """
        struct Struct {}

        fn func() {
            let _ = Struct {};
        }
    """)

    fun `test simple alias, base type with generics as base type`() = doTest("""
        type Foo/*caret*/ = HashSet<i32>;
        fn func() {
            let _: Foo;
        }
        struct HashSet<T> {}
    """, """
        fn func() {
            let _: HashSet<i32>;
        }
        struct HashSet<T> {}
    """)

    fun `test simple alias, base type with generics as path`() = doTest("""
        type Foo/*caret*/ = HashSet<i32>;
        fn func() {
            Foo::new();
        }
        struct HashSet<T> {}
    """, """
        fn func() {
            HashSet::<i32>::new();
        }
        struct HashSet<T> {}
    """)

    fun `test simple alias, base type with generics as path (struct literal)`() = doTest("""
        struct Struct<T> { t: T }
        type Foo/*caret*/ = Struct<i32>;
        fn func() {
            let _ = Foo { t: 0 };
        }
    """, """
        struct Struct<T> { t: T }

        fn func() {
            let _ = Struct::<i32> { t: 0 };
        }
    """)

    fun `test simple alias, array type as base type`() = doTest("""
        type Foo/*caret*/ = [i32];
        fn func() {
            let _: Foo;
        }
    """, """
        fn func() {
            let _: [i32];
        }
    """)

    fun `test simple alias, array type as path`() = doTest("""
        type Foo/*caret*/ = [i32];
        fn func() {
            Foo::new();
        }
    """, """
        fn func() {
            <[i32]>::new();
        }
    """)

    fun `test simple alias, array type as path with type arguments`() = doTest("""
        type Foo/*caret*/ = [i32];
        fn func() {
            Foo::get::<usize>();
        }
    """, """
        fn func() {
            <[i32]>::get::<usize>();
        }
    """)

    fun `test local type alias`() = doTest("""
        fn func() {
            type Foo/*caret*/ = i32;
            let _: Foo;
        }
    """, """
        fn func() {
            let _: i32;
        }
    """)

    fun `test type alias used in other module with import`() = doTest("""
        mod mod1 {
            pub type Foo/*caret*/ = i32;
        }
        mod mod2 {
            use crate::mod1::Foo;
            fn func() {
                let _: Foo;
            }
        }
    """, """
        mod mod1 {}
        mod mod2 {
            fn func() {
                let _: i32;
            }
        }
    """)

    fun `test type alias used in other module without import`() = doTest("""
        mod mod1 {
            pub type Foo/*caret*/ = i32;
        }
        mod mod2 {
            fn func() {
                let _: crate::mod1::Foo;
            }
        }
    """, """
        mod mod1 {}
        mod mod2 {
            fn func() {
                let _: i32;
            }
        }
    """)

    fun `test add imports`() = doTest("""
        mod mod1 {
            pub struct Struct1<T> { t : T }
            pub struct Struct2 {}
            pub type Foo/*caret*/ = Struct1<Struct2>;
        }
        mod mod2 {
            fn func() {
                let _: crate::mod1::Foo;
            }
        }
    """, """
        mod mod1 {
            pub struct Struct1<T> { t : T }
            pub struct Struct2 {}
        }
        mod mod2 {
            use crate::mod1::{Struct1, Struct2};

            fn func() {
                let _: Struct1<Struct2>;
            }
        }
    """)

    fun `test add imports 2`() = doTest("""
        mod mod1 {
            pub mod inner {
                pub struct Struct {}
            }
            pub type Foo/*caret*/ = inner::Struct;
        }
        mod mod2 {
            fn func() {
                let _: crate::mod1::Foo;
            }
        }
    """, """
        mod mod1 {
            pub mod inner {
                pub struct Struct {}
            }
        }
        mod mod2 {
            use crate::mod1::inner;

            fn func() {
                let _: inner::Struct;
            }
        }
    """)

    fun `test qualify path`() = expect<ComparisonFailure> {
    doTest("""
        mod mod1 {
            pub struct Bar {}
            pub type Foo/*caret*/ = Bar;
        }
        mod mod2 {
            struct Bar {}
            fn func() {
                let _: crate::mod1::Foo;
            }
        }
    """, """
        mod mod1 {
            pub struct Bar {}
        }
        mod mod2 {
            struct Bar {}
            fn func() {
                let _: crate::mod1::Bar;
            }
        }
    """)
    }

    fun `test inline called on reference`() = doTest("""
        type Foo = i32;
        fn func() {
            let _: Foo/*caret*/;
        }
    """, """
        fn func() {
            let _: i32;
        }
    """)

    fun `test generic type alias`() = doTest("""
        type /*caret*/Foo<T> = [T];
        fn func(_: Foo<i32>) {}
    """, """
        fn func(_: [i32]) {}
    """)

    fun `test generic type alias with complex inference`() = doTest("""
        struct Bar<T>(T);
        impl <T> Bar<T> {
            fn new(t: T) -> Bar<T> { Bar(t) }
        }

        type Foo<T> = Bar<T>;

        fn foo() {
            let _ = Foo/*caret*/::new(1);
        }
    """, """
        struct Bar<T>(T);
        impl <T> Bar<T> {
            fn new(t: T) -> Bar<T> { Bar(t) }
        }

        fn foo() {
            let _ = Bar::<i32>::new(1);
        }
    """)

    fun `test don't inline associated type`() = doUnavailableTest("""
        fn func(_: <Struct as Trait>::Foo) {}

        struct Struct {}
        impl Trait for Struct {
            type Foo/*caret*/ = i32;
        }
        trait Trait {
            type Foo;
        }
    """)

    fun `test don't inline type alias generated by macro`() = doUnavailableTest("""
        macro_rules! gen {
            () => {
                pub type Foo = i32;
            };
        }
        gen!();
        fn func() {
            let _: Foo/*caret*/;
        }
    """)
}
