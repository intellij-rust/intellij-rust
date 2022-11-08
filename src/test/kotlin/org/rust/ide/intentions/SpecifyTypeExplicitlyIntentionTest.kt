/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class SpecifyTypeExplicitlyIntentionTest : RsIntentionTestBase(SpecifyTypeExplicitlyIntention::class) {
    fun `test availability range 1`() = checkAvailableInSelectionOnly("""
        fn foo() {
            <selection>let mut a;</selection>
            a = 1;
        }
    """)

    fun `test availability range 2`() = checkAvailableInSelectionOnly("""
        fn foo() {
            <selection>let a =</selection> 1;
        }
    """)

    fun `test inferred type`() = doAvailableTest(
        """ fn main() { let var/*caret*/ = 42; } """,
        """ fn main() { let var: i32 = 42; } """
    )

    fun `test generic type`() = doAvailableTest(
        """struct A<T>(T); fn main() { let var/*caret*/ = A(42); } """,
        """struct A<T>(T); fn main() { let var: A<i32> = A(42); } """
    )

    fun `test type with const generic`() = doAvailableTest(
        """struct A<const N: usize>; fn main() { let var/*caret*/ = A::<1>; } """,
        """struct A<const N: usize>; fn main() { let var: A<1> = A::<1>; } """
    )

    fun `test complex pattern`() = doAvailableTest(
        """ fn main() { let (a, b)/*caret*/ = (1, 2); } """,
        """ fn main() { let (a, b): (i32, i32) = (1, 2); } """
    )

    fun `test aliased type`() = doAvailableTest("""
        struct Foo<T>;
        struct Bar<T>;
        type Baz<T> = Foo<Bar<T>>;
        fn foo<T>(c: &Baz<T>) {
            let b/*caret*/ = c;
        }
    """, """
        struct Foo<T>;
        struct Bar<T>;
        type Baz<T> = Foo<Bar<T>>;
        fn foo<T>(c: &Baz<T>) {
            let b: &Baz<T> = c;
        }
    """)

    fun `test type with default type argument`() = doAvailableTest("""
        struct S<T = u32>(S);

        fn foo(c: S<u32>) {
            let b/*caret*/ = c;
        }
    """, """
        struct S<T = u32>(S);

        fn foo(c: S<u32>) {
            let b: S = c;
        }
    """)

    fun `test unavailable in expr 1`() = doUnavailableTest(
        """ fn main() { let var = /*caret*/1; } """
    )

    fun `test unavailable in expr 2`() = doUnavailableTest(
        """ fn main() { let var = 1/*caret*/; } """
    )

    fun `test not inferred type`() = doUnavailableTest(
        """ fn main() { let var/*caret*/ = a; } """
    )

    fun `test generic type with not inferred type`() = doUnavailableTest(
        """struct A<T>(T); fn main() { let var/*caret*/ = A(a); } """
    )

    fun `test generic type with not inferred const generic`() = doUnavailableTest(
        """struct A<const N: usize>; fn main() { let var/*caret*/ = A; } """
    )

    fun `test anon type`() = doUnavailableTest("""
        trait T {}
        fn foo() -> impl T { unimplemented!() }
        fn main() {
            let var/*caret*/ = foo();
        }
    """)

    fun `test import unresolved type`() = doAvailableTest("""
        use crate::a::foo;
        mod a {
            pub struct S;
            pub fn foo() -> S { S }
        }
        fn main() { let var/*caret*/ = foo(); }
    """, """
        use crate::a::{foo, S};
        mod a {
            pub struct S;
            pub fn foo() -> S { S }
        }
        fn main() { let var: S = foo(); }
    """, preview = """
        use crate::a::foo;
        mod a {
            pub struct S;
            pub fn foo() -> S { S }
        }
        fn main() { let var: S = foo(); }
    """)

    fun `test try import unresolved type`() = doAvailableTest("""
            use a::foo;
            mod a {
                struct S;
                pub fn foo() -> S { S }
            }
            fn main() { let var/*caret*/ = foo(); }
    """, """
            use a::foo;
            mod a {
                struct S;
                pub fn foo() -> S { S }
            }
            fn main() { let var: S = foo(); }
    """)

    fun `test import type parameter`() = doAvailableTest("""
            use crate::a::foo;
            mod a {
                pub struct S<T>(T);
                pub struct P;
                pub fn foo() -> S<P> { S(P) }
            }
            fn main() { let var/*caret*/ = foo(); }
    """, """
            use crate::a::{foo, P, S};
            mod a {
                pub struct S<T>(T);
                pub struct P;
                pub fn foo() -> S<P> { S(P) }
            }
            fn main() { let var: S<P> = foo(); }
    """, preview = """
            use crate::a::foo;
            mod a {
                pub struct S<T>(T);
                pub struct P;
                pub fn foo() -> S<P> { S(P) }
            }
            fn main() { let var: S<P> = foo(); }
    """)

    fun `test import type alias`() = doAvailableTest("""
        use crate::a::{foo, A};
        mod a {
            pub struct S;
            pub type A = S;
            pub type B = A;
            pub fn foo() -> B { S }
        }
        fn main() { let var/*caret*/ = foo(); }
    """, """
        use crate::a::{foo, A, B};
        mod a {
            pub struct S;
            pub type A = S;
            pub type B = A;
            pub fn foo() -> B { S }
        }
        fn main() { let var: B = foo(); }
    """, preview = """
        use crate::a::{foo, A};
        mod a {
            pub struct S;
            pub type A = S;
            pub type B = A;
            pub fn foo() -> B { S }
        }
        fn main() { let var: B = foo(); }
    """)

    fun `test import skip default type argument`() = doAvailableTest("""
        use crate::a::foo;
        mod a {
            pub struct R;
            pub struct S<T = R>(T);
            pub fn foo() -> S<R> { S(R) }
        }
        fn main() { let var/*caret*/ = foo(); }
    """, """
        use crate::a::{foo, S};
        mod a {
            pub struct R;
            pub struct S<T = R>(T);
            pub fn foo() -> S<R> { S(R) }
        }
        fn main() { let var: S = foo(); }
    """, preview = """
        use crate::a::foo;
        mod a {
            pub struct R;
            pub struct S<T = R>(T);
            pub fn foo() -> S<R> { S(R) }
        }
        fn main() { let var: S = foo(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test import std type`() = doAvailableTest("""
        use a::foo;

        mod a {
            use std::collections::HashMap;
            pub fn foo() -> HashMap<i32, i32> { HashMap::new() }
        }
        fn main() { let var/*caret*/ = foo(); }
    """, """
        use std::collections::HashMap;
        use a::foo;

        mod a {
            use std::collections::HashMap;
            pub fn foo() -> HashMap<i32, i32> { HashMap::new() }
        }
        fn main() { let var: HashMap<i32, i32> = foo(); }
    """, preview = """
        use a::foo;

        mod a {
            use std::collections::HashMap;
            pub fn foo() -> HashMap<i32, i32> { HashMap::new() }
        }
        fn main() { let var: HashMap<i32, i32> = foo(); }
    """)

    fun `test ref pat`() = doAvailableTest(
        """ fn main() { let ref var/*caret*/ = 42; } """,
        """ fn main() { let ref var: i32 = 42; } """
    )

    fun `test ref mut pat`() = doAvailableTest(
        """ fn main() { let ref mut var/*caret*/ = 42; } """,
        """ fn main() { let ref mut var: i32 = 42; } """
    )

    fun `test import shadowed type`() = doAvailableTest("""
        pub mod m1 {
            pub struct S;
            pub fn foo() -> S { S }
        }
        pub mod m2 { pub struct S; }
        use m2::S;
        use crate::m1::foo;
        fn main() { let s/*caret*/ = foo(); }
    """, """
        pub mod m1 {
            pub struct S;
            pub fn foo() -> S { S }
        }
        pub mod m2 { pub struct S; }
        use m2::S;
        use crate::m1::foo;
        fn main() { let s: crate::m1::S = foo(); }
    """)
}
