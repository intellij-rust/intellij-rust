/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import org.rust.lang.core.psi.RsValueArgumentList

/**
 * Tests for RustParameterInfoHandler
 */
class RsParameterInfoHandlerTest
    : RsParameterInfoHandlerTestBase<RsValueArgumentList, RsArgumentsDescription>(RsParameterInfoHandler()) {
    fun `test fn no args`() = checkByText("""
        fn foo() {}
        fn main() { foo(<caret>); }
    """, "<no arguments>", 0)

    fun `test fn no args before args`() = checkByText("""
        fn foo() {}
        fn main() { foo<caret>(); }
    """, "<no arguments>", -1)

    fun `test fn one arg`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(<caret>); }
    """, "arg: u32", 0)

    fun `test struct one arg`() = checkByText("""
        struct Foo(u32);
        fn main() { Foo(<caret>); }
    """, "_: u32", 0)

    fun `test enum one arg`() = checkByText("""
        enum E  { Foo(u32) }
        fn main() { E::Foo(<caret>); }
    """, "_: u32", 0)

    fun `test fn one arg end`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(42<caret>); }
    """, "arg: u32", 0)

    fun `test fn many args`() = checkByText("""
        fn foo(id: u32, name: &'static str, mut year: &u16) {}
        fn main() { foo(<caret>); }
    """, "id: u32, name: &'static str, year: &u16", 0)

    fun `test fn poorly formatted args`() = checkByText("""
        fn foo(  id   :   u32   , name: &'static str   , mut year   : &u16   ) {}
        fn main() { foo(<caret>); }
    """, "id: u32, name: &'static str, year: &u16", 0)

    fun `test fn arg index0`() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(a1<caret>); }
    """, "a1: u32, a2: u32", 0)

    fun `test fn arg index0 with comma`() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(a1<caret>,); }
    """, "a1: u32, a2: u32", 0)

    fun `test fn arg index1`() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(16,<caret>); }
    """, "a1: u32, a2: u32", 1)

    fun `test fn arg index1 value start`() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(12, <caret>32); }
    """, "a1: u32, a2: u32", 1)

    fun `test fn arg index1 value end`() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(5, 32<caret>); }
    """, "a1: u32, a2: u32", 1)

    fun `test fn arg too many args`() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(0, 32,<caret>); }
    """, "a1: u32, a2: u32", 2)

    fun `test fn closure`() = checkByText("""
        fn foo(fun: Fn(u32) -> u32) {}
        fn main() { foo(|x| x + <caret>); }
    """, "fun: Fn(u32) -> u32", 0)

    fun `test fn nested inner`() = checkByText("""
        fn add(v1: u32, v2: u32) -> u32 { v1 + v2 }
        fn display(v: u32, format: &'static str) {}
        fn main() { display(add(4, <caret>), "0.00"); }
    """, "v1: u32, v2: u32", 1)

    fun `test fn nested outer`() = checkByText("""
        fn add(v1: u32, v2: u32) -> u32 { v1 + v2 }
        fn display(v: u32, indent: bool, format: &'static str) {}
        fn main() { display(add(4, 7), false, <caret>"); }
    """, "v: u32, indent: bool, format: &'static str", 2)

    fun `test multiline`() = checkByText("""
        fn sum(v1: u32, v2: u32, v3: u32) -> u32 { v1 + v2 + v3 }
        fn main() {
            sum(
                10,
                <caret>
            );
        }
    """, "v1: u32, v2: u32, v3: u32", 1)

    fun `test assoc fn`() = checkByText("""
        struct Foo;
        impl Foo { fn new(id: u32, val: f64) {} }
        fn main() {
            Foo::new(10, <caret>);
        }
    """, "id: u32, val: f64", 1)

    fun `test method`() = checkByText("""
        struct Foo;
        impl Foo { fn bar(&self, id: u32, name: &'static name, year: u16) {} }
        fn main() {
            let foo = Foo{};
            foo.bar(10, "Bo<caret>b", 1987);
        }
    """, "id: u32, name: &'static name, year: u16", 1)

    fun `test trait method`() = checkByText("""
        trait Named {
            fn greet(&self, text: &'static str, count: u16, l: f64);
        }
        struct Person;
        impl Named for Person {
            fn greet(&self, text: &'static str, count: u16, l: f64) {}
        }
        fn main() {
            let p = Person {};
            p.greet("Hello", 19, 10.21<caret>);
        }
    """, "text: &'static str, count: u16, l: f64", 2)

    fun `test method with explicit self`() = checkByText("""
        struct S;
        impl S { fn foo(self, arg: u32) {} }

        fn main() {
            let s = S;
            S::foo(s, 0<caret>);
        }
    """, "self, arg: u32", 1)

    fun `test not args 1`() = checkByText("""
        fn foo() {}
        fn main() { fo<caret>o(); }
    """, "", -1)

    fun `test not args 2`() = checkByText("""
        fn foo() {}
        fn main() { foo()<caret>; }
    """, "", -1)

    fun `test not applied within declaration`() = checkByText("""
        fn foo(v<caret>: u32) {}
    """, "", -1)
}
