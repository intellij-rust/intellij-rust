/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.extractEnumVariant.RsExtractEnumVariantAction

class RsExtractEnumVariantTest : RsTestBase() {
    fun `test not available on empty variant`() = doUnavailableTest("""
        enum A {
            /*caret*/V1
        }
    """)

    fun `test not available on variant with discriminant`() = doUnavailableTest("""
        enum A {
            /*caret*/V1 = 1
        }
    """)

    fun `test tuple variant`() = doAvailableTest("""
        enum A {
            /*caret*/V1(i32, bool, String),
            V2
        }
    """, """
        struct /*caret*/V1(pub i32, pub bool, pub String);

        enum A {
            V1(V1),
            V2
        }
    """)

    fun `test struct variant`() = doAvailableTest("""
        enum A {
            /*caret*/V1 { a: i32, b: bool, c: String },
            V2
        }
    """, """
        struct /*caret*/V1 { pub a: i32, pub b: bool, pub c: String }

        enum A {
            V1(V1),
            V2
        }
    """)

    fun `test generic type`() = doAvailableTest("""
        enum A<T> {
            /*caret*/V1(T),
            V2
        }
    """, """
        struct /*caret*/V1<T>(pub T);

        enum A<T> {
            V1(V1<T>),
            V2
        }
    """)

    fun `test type bound`() = doAvailableTest("""
        trait Trait {}
        enum A<T: Trait> {
            /*caret*/V1(T),
            V2
        }
    """, """
        trait Trait {}

        struct /*caret*/V1<T: Trait>(pub T);

        enum A<T: Trait> {
            V1(V1<T>),
            V2
        }
    """)

    fun `test where clause tuple`() = doAvailableTest("""
        trait Trait {}
        enum A<T> where T: Trait {
            /*caret*/V1(T),
            V2
        }
    """, """
        trait Trait {}

        struct /*caret*/V1<T>(pub T) where T: Trait;

        enum A<T> where T: Trait {
            V1(V1<T>),
            V2
        }
    """)

    fun `test where clause struct`() = doAvailableTest("""
        trait Trait {}
        enum A<T> where T: Trait {
            /*caret*/V1 { a: T },
            V2
        }
    """, """
        trait Trait {}

        struct /*caret*/V1<T> where T: Trait { pub a: T }

        enum A<T> where T: Trait {
            V1(V1<T>),
            V2
        }
    """)

    fun `test select only used parameters in where clause`() = doAvailableTest("""
        trait Trait {}
        trait Trait2<S> {
            type Item;
            fn foo() -> S;
        }

        enum A<'a, 'b, 'c, T, S, R, U> where T: Trait + Trait2<S, Item=U>, R: Trait, 'a: 'b + 'c, 'b: 'c {
            /*caret*/V1 { a: &'a T, b: &'b T },
            V2((&'b R, &'c T, S))
        }
    """, """
        trait Trait {}
        trait Trait2<S> {
            type Item;
            fn foo() -> S;
        }

        struct /*caret*/V1<'a, 'b, T, S, U> where 'a: 'b, T: Trait + Trait2<S, Item=U> { pub a: &'a T, pub b: &'b T }

        enum A<'a, 'b, 'c, T, S, R, U> where T: Trait + Trait2<S, Item=U>, R: Trait, 'a: 'b + 'c, 'b: 'c {
            V1(V1<'a, 'b, T, S, U>),
            V2((&'b R, &'c T, S))
        }
    """)

    fun `test lifetime bounds`() = doAvailableTest("""
        enum A<'a: 'b, 'b: 'a> {
            /*caret*/V1 { a: &'a u32, b: &'b u32 }
        }
    """, """
        struct /*caret*/V1<'a: 'b, 'b: 'a> { pub a: &'a u32, pub b: &'b u32 }

        enum A<'a: 'b, 'b: 'a> {
            V1(V1<'a, 'b>)
        }
    """)

    fun `test type parameter bounded by lifetime`() = doAvailableTest("""
        trait Sync {}

        enum S<'a, T: 'a> where &'a T: Sync {
            /*caret*/V1(T),
            V2
        }
    """, """
        trait Sync {}

        struct /*caret*/V1<'a, T: 'a>(pub T) where &'a T: Sync;

        enum S<'a, T: 'a> where &'a T: Sync {
            V1(V1<'a, T>),
            V2
        }
    """)

    fun `test transitive lifetime bound`() = doAvailableTest("""
        trait Sync {}

        enum S<'a: 'b, 'b, T: 'a> where &'b T: Sync {
            /*caret*/V1(&'a T),
            V2(&'b u32)
        }
    """, """
        trait Sync {}

        struct /*caret*/V1<'a: 'b, 'b, T: 'a>(pub &'a T) where &'b T: Sync;

        enum S<'a: 'b, 'b, T: 'a> where &'b T: Sync {
            V1(V1<'a, 'b, T>),
            V2(&'b u32)
        }
    """)

    fun `test const generics`() = doAvailableTest("""
        trait Trait {}
        enum A<const T: usize> {
            /*caret*/V1 { a: [u32; T] },
            V2
        }
    """, """
        trait Trait {}

        struct /*caret*/V1<const T: usize> { pub a: [u32; T] }

        enum A<const T: usize> {
            V1(V1<{ T }>),
            V2
        }
    """)

    fun `test lifetime`() = doAvailableTest("""
        enum A<'a> {
            /*caret*/V1(&'a u32),
            V2
        }
    """, """
        struct /*caret*/V1<'a>(pub &'a u32);

        enum A<'a> {
            V1(V1<'a>),
            V2
        }
    """)

    fun `test skip unused generic type`() = doAvailableTest("""
        struct S<X> { a: X }

        enum A<'a, T1, T2, T3, T4> {
            /*caret*/V1(T1, &'a T2, S<S<T4>>),
            V2(T3)
        }
    """, """
        struct S<X> { a: X }

        struct /*caret*/V1<'a, T1, T2, T4>(pub T1, pub &'a T2, pub S<S<T4>>);

        enum A<'a, T1, T2, T3, T4> {
            V1(V1<'a, T1, T2, T4>),
            V2(T3)
        }
    """)

    fun `test skip unused lifetime`() = doAvailableTest("""
        struct S<X> { a: X }

        enum A<'a, 'b> {
            /*caret*/V1(S<S<&'a u32>>),
            V2('b u32)
        }
    """, """
        struct S<X> { a: X }

        struct /*caret*/V1<'a>(pub S<S<&'a u32>>);

        enum A<'a, 'b> {
            V1(V1<'a>),
            V2('b u32)
        }
    """)

    fun `test replace usage tuple in pattern`() = doAvailableTest("""
        enum A {
            /*caret*/V1(u32, bool, i32)
        }

        enum Option<T> { Some(T), None }

        fn foo(mut a: A, mut b: Option<A>) {
            match a {
                A::V1(ref mut x, ref y, ..) => {},
            }

            if let Option::Some(A::V1(ref mut x, ref y, z)) = b {

            }
        }
    """, """
        struct /*caret*/V1(pub u32, pub bool, pub i32);

        enum A {
            V1(V1)
        }

        enum Option<T> { Some(T), None }

        fn foo(mut a: A, mut b: Option<A>) {
            match a {
                A::V1(V1(ref mut x, ref y, ..)) => {},
            }

            if let Option::Some(A::V1(V1(ref mut x, ref y, z))) = b {

            }
        }
    """)

    fun `test replace usage struct in pattern`() = doAvailableTest("""
        enum A {
            /*caret*/V1 { x: u32, y: bool, z: i32 }
        }

        enum Option<T> { Some(T), None }

        fn foo(mut a: A, mut b: Option<A>) {
            match a {
                A::V1 { ref mut x, y: ref u, .. } => {}
            }

            if let Option::Some(A::V1 { ref mut x, ref y, z }) = b {

            }
        }
    """, """
        struct /*caret*/V1 { pub x: u32, pub y: bool, pub z: i32 }

        enum A {
            V1(V1)
        }

        enum Option<T> { Some(T), None }

        fn foo(mut a: A, mut b: Option<A>) {
            match a {
                A::V1(V1 { ref mut x, y: ref u, .. }) => {}
            }

            if let Option::Some(A::V1(V1 { ref mut x, ref y, z })) = b {

            }
        }
    """)

    fun `test replace usage struct`() = doAvailableTest("""
        enum E {
            /*caret*/V1 { x: i32, y: u32, z: u32 },
            V2
        }

        fn foo() {
            let z = 5;
            let v = E::V1 { x: 42, y: 50, z };
            let v = E::V1 { x: 42, y: 50 /* comment */, z: 3 };
        }
    """, """
        struct /*caret*/V1 { pub x: i32, pub y: u32, pub z: u32 }

        enum E {
            V1(V1),
            V2
        }

        fn foo() {
            let z = 5;
            let v = E::V1(V1 { x: 42, y: 50, z });
            let v = E::V1(V1 { x: 42, y: 50 /* comment */, z: 3 });
        }
    """)

    fun `test replace usage tuple`() = doAvailableTest("""
        enum E {
            /*caret*/V1(i32, String),
            V2
        }

        fn foo() {
            let v = E::V1(1, String::new());
            let v = E::V1(1, /*comment*/ String::new());
        }
    """, """
        struct /*caret*/V1(pub i32, pub String);

        enum E {
            V1(V1),
            V2
        }

        fn foo() {
            let v = E::V1(V1(1, String::new()));
            let v = E::V1(V1(1, /*comment*/ String::new()));
        }
    """)

    fun `test generated struct has same visibility`() = doAvailableTest("""
        pub enum A {
            /*caret*/V1 { a: i32, b: bool, c: String },
            V2
        }
    """, """
        pub struct /*caret*/V1 { pub a: i32, pub b: bool, pub c: String }

        pub enum A {
            V1(V1),
            V2
        }
    """)

    fun `test add pub to all struct's fields with default vis`() = doAvailableTest("""
        enum A {
            /*caret*/V1 { /* comment */a: i32, /* comment */b: i32, pub c: i32, pub(crate) d: i32 },
            V2
        }
    """, """
        struct /*caret*/V1 { /* comment */pub a: i32, /* comment */pub b: i32, pub c: i32, pub(crate) d: i32 }

        enum A {
            V1(V1),
            V2
        }
    """)

    fun `test add pub to all tuple's fields with default vis`() = doAvailableTest("""
        enum A {
            /*caret*/V1(/* comment */i32, /* comment */i32, pub i32, pub(crate) i32),
            V2
        }
    """, """
        struct /*caret*/V1(/* comment */pub i32, /* comment */pub i32, pub i32, pub(crate) i32);

        enum A {
            V1(V1),
            V2
        }
    """)

    fun `test import generated struct if needed`() = doAvailableTest("""
        use a::E;

        mod a {
            pub enum E {
                /*caret*/V1 { x: i32, y: i32 },
                V2
            }
        }

        fn main() {
            let _ = E::V1 { x: 0, y: 1 };
        }
    """, """
        use a::{E, V1};

        mod a {
            pub struct /*caret*/V1 { pub x: i32, pub y: i32 }

            pub enum E {
                V1(V1),
                V2
            }
        }

        fn main() {
            let _ = E::V1(V1 { x: 0, y: 1 });
        }
    """)

    fun `test import generated tuple if needed`() = doAvailableTest("""
        use a::E;

        mod a {
            pub enum E {
                /*caret*/V1(i32, i32),
                V2
            }
        }

        fn main() {
            let _ = E::V1(0, 1);
        }
    """, """
        use a::{E, V1};

        mod a {
            pub struct /*caret*/V1(pub i32, pub i32);

            pub enum E {
                V1(V1),
                V2
            }
        }

        fn main() {
            let _ = E::V1(V1(0, 1));
        }
    """)

    // TODO: fix
    fun `test don't import generated struct if its name already in scope`() = doAvailableTest("""
        use a::E;

        struct V1;

        mod a {
            pub enum E {
                /*caret*/V1 { x: i32, y: i32 },
                V2
            }
        }

        fn main() {
            let _ = E::V1 { x: 0, y: 1 };
        }
    """, """
        use a::E;

        struct V1;

        mod a {
            pub struct /*caret*/V1 { pub x: i32, pub y: i32 }

            pub enum E {
                V1(V1),
                V2
            }
        }

        fn main() {
            let _ = E::V1(V1 { x: 0, y: 1 });
        }
    """)

    fun `test keep supported attributes`() = doAvailableTest("""
        #[derive(Debug, Clone)]
        #[repr(C)]
        pub enum E {
            /*caret*/V1 { x: i32, y: i32 },
            V2
        }
    """, """
        #[derive(Debug, Clone)]
        #[repr(C)]
        pub struct /*caret*/V1 { pub x: i32, pub y: i32 }

        #[derive(Debug, Clone)]
        #[repr(C)]
        pub enum E {
            V1(V1),
            V2
        }
    """)

    fun `test ignore unsupported attributes`() = doAvailableTest("""
        #[attr]
        pub enum E {
            /*caret*/V1 { x: i32, y: i32 },
            V2
        }
    """, """
        pub struct /*caret*/V1 { pub x: i32, pub y: i32 }

        #[attr]
        pub enum E {
            V1(V1),
            V2
        }
    """)

    fun `test reference to tuple constructor`() = doAvailableTest("""
        mod foo {
            pub enum Foo { Bar/*caret*/(i32, u32) }
        }

        fn main() {
            let ctr = foo::Foo::Bar;
            let ctr2 = ctr;
            let f = ctr2(0, 1);
        }
    """, """
        use foo::Bar;

        mod foo {
            pub struct Bar(pub i32, pub u32);

            pub enum Foo { Bar(Bar) }
        }

        fn main() {
            let ctr = |p0, p1| foo::Foo::Bar(Bar(p0, p1));
            let ctr2 = ctr;
            let f = ctr2(0, 1);
        }
    """)

    fun `test tuple constructor in a function call`() = doAvailableTest("""
        enum Foo { Bar/*caret*/(i32) }
        fn id<T>(x: T) -> T { x }

        fn main() {
            id(Foo::Bar)(42);
        }
    """, """
        struct Bar(pub i32);

        enum Foo { Bar(Bar) }
        fn id<T>(x: T) -> T { x }

        fn main() {
            id(|p0| Foo::Bar(Bar(p0)))(42);
        }
    """)

    private fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkEditorAction(before, after, "Rust.RsExtractEnumVariant")
    }

    private fun doUnavailableTest(@Language("Rust") code: String) {
        InlineFile(code.trimIndent()).withCaret()
        check(!myFixture.testAction(RsExtractEnumVariantAction()).isEnabled)
    }
}
