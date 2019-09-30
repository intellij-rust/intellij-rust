/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class SpecifyTypeExplicitlyIntentionTest : RsIntentionTestBase(SpecifyTypeExplicitlyIntention()) {
    fun `test inferred type`() = doAvailableTest(
        """ fn main() { let var/*caret*/ = 42; } """,
        """ fn main() { let var: i32 = 42; } """
    )

    fun `test generic type`() = doAvailableTest(
        """struct A<T>(T);fn main() { let var/*caret*/ = A(42); } """,
        """struct A<T>(T);fn main() { let var: A<i32> = A(42); } """
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

    fun `test not inferred type`() = doUnavailableTest(
        """ fn main() { let var/*caret*/ = a; } """
    )

    fun `test generic type with not inferred type`() = doUnavailableTest(
        """struct A<T>(T);fn main() { let var/*caret*/ = A(a); } """
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
}
