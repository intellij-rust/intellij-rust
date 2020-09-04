/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class SpecifyTypeExplicitlyIntentionTest : RsIntentionTestBase(SpecifyTypeExplicitlyIntention::class) {
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
            use a::foo;
            mod a {
                pub struct S;
                pub fn foo() -> S { S }
            }
            fn main() { let var/*caret*/ = foo(); }
    """, """
            use a::{foo, S};
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
            use a::foo;
            mod a {
                pub struct S<T>(T);
                pub struct P;
                pub fn foo() -> S<P> { S(P) }
            }
            fn main() { let var/*caret*/ = foo(); }
    """, """
            use a::{foo, P, S};
            mod a {
                pub struct S<T>(T);
                pub struct P;
                pub fn foo() -> S<P> { S(P) }
            }
            fn main() { let var: S<P> = foo(); }
    """)

    fun `test import type alias`() = doAvailableTest("""
            use a::{foo, A};
            mod a {
                pub struct S;
                pub type A = S;
                pub type B = A;
                pub fn foo() -> B { S }
            }
            fn main() { let var/*caret*/ = foo(); }
    """, """
            use a::{foo, A, B};
            mod a {
                pub struct S;
                pub type A = S;
                pub type B = A;
                pub fn foo() -> B { S }
            }
            fn main() { let var: B = foo(); }
    """)

    // TODO: Don't render default values of type parameters
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test import std type`() = doAvailableTest("""
            use a::foo;

            mod a {
                use std::collections::HashMap;
                pub fn foo() -> HashMap<i32, i32> { HashMap::new() }
            }
            fn main() { let var/*caret*/ = foo(); }
    """, """
            use a::foo;
            use std::collections::hash_map::RandomState;
            use std::collections::HashMap;

            mod a {
                use std::collections::HashMap;
                pub fn foo() -> HashMap<i32, i32> { HashMap::new() }
            }
            fn main() { let var: HashMap<i32, i32, RandomState> = foo(); }
    """)

    fun `test ref pat`() = doAvailableTest(
        """ fn main() { let ref var/*caret*/ = 42; } """,
        """ fn main() { let ref var: i32 = 42; } """
    )

    fun `test ref mut pat`() = doAvailableTest(
        """ fn main() { let ref mut var/*caret*/ = 42; } """,
        """ fn main() { let ref mut var: i32 = 42; } """
    )
}
