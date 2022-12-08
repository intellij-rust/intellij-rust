/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.MockAdditionalCfgOptions

class UnElideLifetimesIntentionTest : RsIntentionTestBase(UnElideLifetimesIntention::class) {
    fun `test unavailable without references 1`() = doUnavailableTest("""
        fn bar/*caret*/(x: i32) -> i32 {}
    """)

    fun `test unavailable without references 2`() = doUnavailableTest("""
        fn bar/*caret*/(x: T) -> T {}
        struct T;
    """)

    fun `test unavailable in block body`() = doUnavailableTest("""
        fn bar(x: &i32) {/*caret*/}
    """)

    fun `test unavailable in doc comment`() = doUnavailableTest("""
        /// ```
        /// /*caret*/
        /// ```
        fn bar(x: &i32) {}
    """)

    fun `test unavailable without args`() = doUnavailableTest("""
        fn bar(/*caret*/) {}
    """)

    fun `test unavailable with explicit lifetime`() = doUnavailableTest("""
        fn bar<'a>(x: &'a /*caret*/ i32) {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test unavailable if there are parameters under cfg attributes`() = doUnavailableTest("""
        fn foo(#[cfg(intellij_rust)] p: &/*caret*/ i32, #[cfg(not(intellij_rust))] p: &/*caret*/ u32) -> & i32 { p }
    """)

    fun `test simple`() = doAvailableTest("""
        fn foo(p: &/*caret*/ i32) -> & i32 { p }
    """, """
        fn foo<'a>(p: &/*caret*/'a i32) -> &'a i32 { p }
    """)

    fun `test all generics`() = doAvailableTest("""
        fn foo<T, const N: usize, U>(p: &/*caret*/ i32) -> & i32 { p }
    """, """
        fn foo<'a, T, const N: usize, U>(p: &/*caret*/'a i32) -> &'a i32 { p }
    """)

    fun `test mut ref`() = doAvailableTest("""
        fn foo(p: &/*caret*/mut i32) -> & i32 { p }
    """, """
        fn foo<'a>(p: &/*caret*/'a mut i32) -> &'a i32 { p }
    """)

    fun `test nested ref`() = doAvailableTest("""
        fn foo(p: &&/*caret*/ i32) -> & i32 { unimplemented!() }
    """, """
        fn foo<'a>(p: &'a &/*caret*/i32) -> &'a i32 { unimplemented!() }
    """)

    fun `test generic type`() = doAvailableTest("""
        fn foo<T>(p1:/*caret*/ &i32, p2: T) -> & i32 { p }
    """, """
        fn foo<'a, T>(p1:/*caret*/ &'a i32, p2: T) -> &'a i32 { p }
    """)

    fun `test lifetime type as parameter`() = doAvailableTest("""
        struct S<'a> { x: &'a u32 }
        fn make_s(x:/*caret*/ S) { unimplemented!() }
    """, """
        struct S<'a> { x: &'a u32 }
        fn make_s<'a>(x:/*caret*/ S<'a>) { unimplemented!() }
    """)

    fun `test lifetime type as return value`() = doAvailableTest("""
        struct S<'a> { x: &'a i32 }
        fn make_s(x:/*caret*/ &i32) -> S { unimplemented!() }
    """, """
        struct S<'a> { x: &'a i32 }
        fn make_s<'a>(x:/*caret*/ &'a i32) -> S<'a> { unimplemented!() }
    """)

    fun `test struct parameter with multiple lifetimes`() = doAvailableTest("""
        struct S<'a, 'b> { x: &'a u32, y: &'b u32 }
        fn make_s(x:/*caret*/ S) { unimplemented!() }
    """, """
        struct S<'a, 'b> { x: &'a u32, y: &'b u32 }
        fn make_s<'a, 'b>(x:/*caret*/ S<'a, 'b>) { unimplemented!() }
    """)

    fun `test struct return type with multiple lifetimes`() = doUnavailableTest("""
        struct S<'a, 'b> { x: &'a u32, y: &'b u32 }
        fn make_s(x:/*caret*/ &i32) -> S { unimplemented!() }
    """)

    fun `test lifetime type complex struct`() = doAvailableTest("""
        struct S<'a, T, X> { x: &'a T, y: X }
        fn make_s<X>(x:/*caret*/ S<u32, X>) -> S<u32, X> { unimplemented!() }
    """, """
        struct S<'a, T, X> { x: &'a T, y: X }
        fn make_s<'a, X>(x:/*caret*/ S<'a, u32, X>) -> S<'a, u32, X> { unimplemented!() }
    """)

    fun `test unknown`() = doAvailableTest("""
        fn foo(p1: &i32,/*caret*/ p2: &i32) -> &i32 { p2 }
    """, """
        fn foo<'a, 'b>(p1: &'a i32, p2: &'b i32) -> &'<selection>_</selection> i32 { p2 }
    """)

    // TODO: support nested types
    fun `test nested adt`() = doAvailableTest("""
        struct S<'a, T>(&'a T);
        fn /*caret*/foo(s: S<S<i32>>) -> S<S<i32>> { s }
    """, """
        struct S<'a, T>(&'a T);
        fn /*caret*/foo<'a>(s: S<'a, S<i32>>) -> S<'a, S<i32>> { s }
    """)

    fun `test method decl`() = doAvailableTest("""
        trait Foo {
            fn /*caret*/bar(&self, x: &i32, y: &i32, z: i32) -> &i32;
        }
    """, """
        trait Foo {
            fn /*caret*/bar<'a, 'b, 'c>(&'a self, x: &'b i32, y: &'c i32, z: i32) -> &'a i32;
        }
    """)

    fun `test method impl`() = doAvailableTest("""
        trait Foo {
            fn bar(&self, x: &i32, y: &i32, z: i32) -> &i32;
        }
        struct S {}
        impl Foo for S {
            fn /*caret*/bar(&self, x: &i32, y: &i32, z: i32) -> &i32 {
                unimplemented!()
            }
        }
    """, """
        trait Foo {
            fn bar(&self, x: &i32, y: &i32, z: i32) -> &i32;
        }
        struct S {}
        impl Foo for S {
            fn /*caret*/bar<'a, 'b, 'c>(&'a self, x: &'b i32, y: &'c i32, z: i32) -> &'a i32 {
                unimplemented!()
            }
        }
    """)
}
