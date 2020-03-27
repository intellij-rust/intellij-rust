/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ExtractEnumVariantIntentionTest : RsIntentionTestBase(ExtractEnumVariantIntention()) {
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
        struct /*caret*/V1(i32, bool, String);

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
        struct /*caret*/V1 { a: i32, b: bool, c: String }

        enum A {
            V1(V1),
            V2
        }
    """)

    fun `test extract pub visibility`() = doAvailableTest("""
        enum A {
            /*caret*/V1 { pub a: i32, pub(crate) b: bool, c: String },
            V2
        }
    """, """
        struct /*caret*/V1 { pub a: i32, pub(crate) b: bool, c: String }

        enum A {
            V1(pub V1),
            V2
        }
    """)

    fun `test extract crate visibility`() = doAvailableTest("""
        enum A {
            /*caret*/V1 { a: i32, pub(crate) b: bool, c: String },
            V2
        }
    """, """
        struct /*caret*/V1 { a: i32, pub(crate) b: bool, c: String }

        enum A {
            V1(pub(crate) V1),
            V2
        }
    """)

    fun `test generic type`() = doAvailableTest("""
        enum A<T> {
            /*caret*/V1(T),
            V2
        }
    """, """
        struct /*caret*/V1<T>(T);

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

        struct /*caret*/V1<T: Trait>(T);

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

        struct /*caret*/V1<T>(T) where T: Trait;

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

        struct /*caret*/V1<T> where T: Trait { a: T }

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

        struct /*caret*/V1<'a, 'b, T, S, U> where T: Trait + Trait2<S, Item=U>, 'a: 'b { a: &'a T, b: &'b T }

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
        struct /*caret*/V1<'a: 'b, 'b: 'a> { a: &'a u32, b: &'b u32 }

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

        struct /*caret*/V1<'a, T: 'a>(T) where &'a T: Sync;

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

        struct /*caret*/V1<'a: 'b, 'b, T: 'a>(&'a T) where &'b T: Sync;

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

        struct /*caret*/V1<const T: usize> { a: [u32; T] }

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
        struct /*caret*/V1<'a>(&'a u32);

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

        struct /*caret*/V1<'a, T1, T2, T4>(T1, &'a T2, S<S<T4>>);

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

        struct /*caret*/V1<'a>(S<S<&'a u32>>);

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
        struct /*caret*/V1(u32, bool, i32);

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
        struct /*caret*/V1 { x: u32, y: bool, z: i32 }

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
        struct /*caret*/V1 { x: i32, y: u32, z: u32 }

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
            /*caret*/V1(i32, u32),
            V2
        }

        fn foo() {
            let v = E::V1(1, 2);
            let v = E::V1(1, /*comment*/ 2);
        }
    """, """
        struct /*caret*/V1(i32, u32);

        enum E {
            V1(V1),
            V2
        }

        fn foo() {
            let v = E::V1(V1(1, 2));
            let v = E::V1(V1(1, /*comment*/ 2));
        }
    """)
}
