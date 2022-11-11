/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class ChangeFunctionSignatureFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test no parameters add one parameter`() = checkFixByText("Add `i32` as `1st` parameter to function `foo`", """
        fn foo() {}

        fn main() {
            foo<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>1/*caret*/</error>)</error>;
        }
    """, """
        fn foo(i: i32) {}

        fn main() {
            foo(1);
        }
    """, preview = null)

    fun `test no parameters add multiple parameters`() = checkFixByText("<html>Change signature to foo(<b>i32</b>, <b>bool</b>)</html>", """
        fn foo() {}

        fn main() {
            foo<error descr="This function takes 0 parameters but 2 parameters were supplied [E0061]">(<error>1/*caret*/</error>, <error>false</error>)</error>;
        }
    """, """
        fn foo(i: i32, x: bool) {}

        fn main() {
            foo(1, false);
        }
    """, preview = null)

    fun `test add additional parameter same type forward`() = checkFixByText("Add `i32` as `1st` parameter to function `foo`", """
        fn foo(a: i32) {}

        fn main() {
            foo<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(0, <error>1/*caret*/</error>)</error>;
            foo(5);
        }
    """, """
        fn foo(i: i32, a: i32) {}

        fn main() {
            foo(0, 1);
            foo(, 5);
        }
    """, preview = null)

    fun `test add additional parameter same type backward`() = checkFixByText("Add `i32` as `2nd` parameter to function `foo`", """
        fn foo(a: i32) {}

        fn main() {
            foo<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(0, <error>1/*caret*/</error>)</error>;
            foo(5,);
        }
    """, """
        fn foo(a: i32, i: i32) {}

        fn main() {
            foo(0, 1);
            foo(5, );
        }
    """, preview = null)

    fun `test add additional parameter different type`() = checkFixByText("Add `bool` as `2nd` parameter to function `foo`", """
        fn foo(a: i32) {}

        fn main() {
            foo<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(0, <error>false/*caret*/</error>)</error>;
            foo(5);
        }
    """, """
        fn foo(a: i32, x: bool) {}

        fn main() {
            foo(0, false);
            foo(5, );
        }
    """, preview = null)

    fun `test add multiple additional parameters forward`() = checkFixByText("<html>Change signature to foo(i32, <b>bool</b>, i32, <b>i32</b>)</html>", """
        fn foo(a: i32, b: i32) {}

        fn main() {
            foo<error descr="This function takes 2 parameters but 4 parameters were supplied [E0061]">(0, false, <error>3/*caret*/</error>, <error>4</error>)</error>;
            foo(0, 1);
        }
    """, """
        fn foo(a: i32, b0: bool, b: i32, i: i32) {}

        fn main() {
            foo(0, false, 3, 4);
            foo(0, , 1, );
        }
    """, preview = null)

    fun `test add multiple additional parameters backward`() = checkFixByText("<html>Change signature to foo(<b>i32</b>, <b>bool</b>, i32, i32)</html>", """
        fn foo(a: i32, b: i32) {}

        fn main() {
            foo<error descr="This function takes 2 parameters but 4 parameters were supplied [E0061]">(0, false, <error>3/*caret*/</error>, <error>4</error>)</error>;
            foo(0, 1);
        }
    """, """
        fn foo(i: i32, b0: bool, a: i32, b: i32) {}

        fn main() {
            foo(0, false, 3, 4);
            foo(, , 0, 1);
        }
    """, preview = null)

    fun `test change signature total type mismatch`() = checkFixByText("<html>Change signature to foo(<b>bool</b>, <b>bool</b>, <b>bool</b>)</html>", """
        fn foo(a: i32, b: i32) {}

        fn main() {
            foo<error>(true, true, <error>true/*caret*/</error>)</error>;
        }
    """, """
        fn foo(a: bool, b: bool, x: bool) {}

        fn main() {
            foo(true, true, true);
        }
    """, preview = null)

    fun `test do not offer add parameter fix if argument count would not match 1`() = checkFixIsUnavailable("<html>Change signature to foo(<b>bool</b>, <b>bool</b>, <b>bool</b>, i32, i32)</html>", """
        fn foo(a: i32, b: i32) {}

        fn main() {
            foo<error>(true, false, <error>true/*caret*/</error>)</error>;
        }
    """)

    fun `test do not offer add parameter fix if argument count would not match 2`() = checkFixIsUnavailable("<html>Change signature to foo(i32, i32, <b>bool</b>, <b>bool</b>, <b>bool</b>)</html>", """
        fn foo(a: i32, b: i32) {}

        fn main() {
            foo<error>(true, false, <error>true/*caret*/</error>)</error>;
        }
    """)

    fun `test add parameter to method`() = checkFixByText("Add `i32` as `1st` parameter to method `foo`", """
        struct S;
        impl S {
            fn foo(&self) {}
        }

        fn bar(s: S) {
            s.foo<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>0/*caret*/</error>)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&self, i: i32) {}
        }

        fn bar(s: S) {
            s.foo(0);
        }
    """, preview = null)

    fun `test add unknown type`() = checkFixByText("Add `_` as `1st` parameter to function `foo`", """
        fn foo() {}

        fn main() {
            foo<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>Unknown/*caret*/</error>)</error>;
        }
    """, """
        fn foo(x: _) {}

        fn main() {
            foo(Unknown);
        }
    """, preview = null)

    fun `test add aliased type`() = checkFixByText("Add `Foo` as `1st` parameter to function `foo`", """
        fn foo() {}

        type Foo = u32;

        fn bar(f: Foo) {
            foo<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>f/*caret*/</error>)</error>;
        }
    """, """
        fn foo(f: Foo) {}

        type Foo = u32;

        fn bar(f: Foo) {
            foo(f);
        }
    """, preview = null)

    fun `test add type with default type argument`() = checkFixByText("Add `Foo` as `1st` parameter to function `foo`", """
        fn foo() {}

        struct Foo<T = u32>(T);

        fn bar(f: Foo) {
            foo<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>f/*caret*/</error>)</error>;
        }
    """, """
        fn foo(f: Foo) {}

        struct Foo<T = u32>(T);

        fn bar(f: Foo) {
            foo(f);
        }
    """, preview = null)

    fun `test import added argument type`() = checkFixByText("Add `S` as `1st` parameter to function `bar`", """
        mod foo {
            pub fn bar() {}
        }

        pub struct S;

        fn main() {
            let s = S;
            foo::bar<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>s/*caret*/</error>)</error>;
        }
    """, """
        mod foo {
            use crate::S;

            pub fn bar(s: S) {}
        }

        pub struct S;

        fn main() {
            let s = S;
            foo::bar(s);
        }
    """, preview = null)

    fun `test import changed argument type`() = checkFixByText("Change type of parameter `a` of function `bar` to `S`", """
        mod foo {
            pub fn bar(a: u32) {}
        }

        pub struct S;

        fn main() {
            let s = S;
            foo::bar<error>(s/*caret*/)</error>;
        }
    """, """
        mod foo {
            use crate::S;

            pub fn bar(a: S) {}
        }

        pub struct S;

        fn main() {
            let s = S;
            foo::bar(s);
        }
    """, preview = null)

    fun `test do not offer on tuple struct constructors`() = checkFixIsUnavailable("Add", """
        struct S(u32);

        fn main() {
            let s = S<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(0, <error>1/*caret*/</error>)</error>;
        }
    """)

    fun `test suggest parameter name`() = checkFixByText("Add `i32` as `1st` parameter to function `foo`", """
        fn foo() {}

        fn main() {
            let x = 5;
            foo<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(<error>x/*caret*/</error>)</error>;
        }
    """, """
        fn foo(x: i32) {}

        fn main() {
            let x = 5;
            foo(x);
        }
    """, preview = null)

    fun `test skip existing parameter name`() = checkFixByText("Add `i32` as `2nd` parameter to function `foo`", """
        fn foo(i: i32) {}

        fn main() {
            foo<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(1/*caret*/, <error>2</error>)</error>;
        }
    """, """
        fn foo(i: i32, i0: i32) {}

        fn main() {
            foo(1, 2);
        }
    """, preview = null)

    fun `test unavailable on correct method UFCS`() = checkFixIsUnavailable("Change", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            S::foo(1/*caret*/, 1);
        }
    """)

    fun `test unavailable with disabled parameter 1`() = checkFixIsUnavailable("Change", """
        fn foo(a: u32, #[cfg(foo)] b: u32, c: u32) {}
        fn bar() {
            foo(1, true/*caret*/);
        }
    """)

    fun `test unavailable with disabled parameter 2`() = checkFixIsUnavailable("Add `i32`", """
        fn foo(i: i32, #[cfg(foo)] b: u32) {}

        fn main() {
            foo<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(1/*caret*/, <error>2</error>)</error>;
        }
    """)

    fun `test change type simple binding`() = checkFixByText("Change type of parameter `a` of function `foo` to `bool`", """
        fn foo(a: u32) {}
        fn bar() {
            foo<error>(true/*caret*/)</error>;
        }
    """, """
        fn foo(a: bool) {}
        fn bar() {
            foo(true);
        }
    """, preview = null)

    fun `test change type complex binding`() = checkFixByText("Change type of `1st` parameter of function `foo` to `bool`", """
        fn foo((a, b): (u32, u32)) {}
        fn bar() {
            foo<error>(true/*caret*/)</error>;
        }
    """, """
        fn foo((a, b): bool) {}
        fn bar() {
            foo(true);
        }
    """, preview = null)

    fun `test change multiple parameter types`() = checkFixByText("<html>Change signature to foo(<b>bool</b>, <b>&str</b>)</html>", """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo<error>(true/*caret*/, "foo")</error>;
        }
    """, """
        fn foo(a: bool, b: &str) {}
        fn bar() {
            foo(true/*caret*/, "foo");
        }
    """, preview = null)

    fun `test change type parameter in the middle`() = checkFixByText("Change type of `2nd` parameter of function `foo` to `bool`", """
        fn foo(x: u32, (a, b): (u32, u32), c: u32) {}
        fn bar() {
            foo<error>(0, true/*caret*/, 1)</error>;
        }
    """, """
        fn foo(x: u32, (a, b): bool, c: u32) {}
        fn bar() {
            foo(0, true, 1);
        }
    """, preview = null)

    fun `test remove parameter simple binding`() = checkFixByText("Remove parameter `b` from function `foo`", """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo<error>(0/*caret*/<error>)</error></error>;
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo(0);
        }
    """, preview = null)

    fun `test remove parameter complex binding`() = checkFixByText("Remove `2nd` parameter from function `foo`", """
        fn foo(x: u32, (a, b): (u32, u32)) {}
        fn bar() {
            foo<error>(0/*caret*/<error>)</error></error>;
        }
    """, """
        fn foo(x: u32) {}
        fn bar() {
            foo(0);
        }
    """, preview = null)

    fun `test remove multiple parameters`() = checkFixByText("<html>Change signature to foo()</html>", """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo<error>(/*caret*/<error>)</error></error>;
        }
    """, """
        fn foo() {}
        fn bar() {
            foo();
        }
    """, preview = null)

    fun `test change type and remove parameters`() = checkFixByText("<html>Change signature to foo(<b>bool</b>)</html>", """
        fn foo(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo<error>(true/*caret*/<error>)</error></error>;
        }
    """, """
        fn foo(a: bool) {}
        fn bar() {
            foo(true);
        }
    """, preview = null)

    fun `test remove parameters change usage`() = checkFixByText("<html>Change signature to foo()</html>", """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo<error>(/*caret*/<error>)</error></error>;
            foo(1, 2);
        }
    """, """
        fn foo() {}
        fn bar() {
            foo();
            foo();
        }
    """, preview = null)

    fun `test change method type`() = checkFixByText("Change type of parameter `a` of method `foo` to `&str`", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            s.foo<error>(""/*caret*/)</error>;
        }
    """, """
        struct S {}
        impl S {
            fn foo(&self, a: &str) {}
        }

        fn bar(s: S) {
            s.foo("");
        }
    """, preview = null)

    fun `test change method parameter type UFCS`() = checkFixByText("Change type of parameter `a` of method `foo` to `&str`", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            S::foo<error>(&s, ""/*caret*/)</error>;
        }
    """, """
        struct S {}
        impl S {
            fn foo(&self, a: &str) {}
        }

        fn bar(s: S) {
            S::foo(&s, "");
        }
    """, preview = null)

    fun `test remove method parameter type UFCS`() = checkFixByText("Remove parameter `a` from method `foo`", """
        struct S {}
        impl S {
            fn foo(&self, a: u32) {}
        }

        fn bar(s: S) {
            S::foo<error>(&s/*caret*/<error>)</error></error>;
        }
    """, """
        struct S {}
        impl S {
            fn foo(&self) {}
        }

        fn bar(s: S) {
            S::foo(&s);
        }
    """, preview = null)

    fun `test add method parameter UFCS`() = checkFixByText("Add `bool` as `1st` parameter to method `foo`", """
        struct S {}
        impl S {
            fn foo(&self, a: i32) {}
        }

        fn bar(s: S) {
            S::foo<error>(&s/*caret*/, true, <error>1</error>)</error>;
        }
    """, """
        struct S {}
        impl S {
            fn foo(&self, x: bool, a: i32) {}
        }

        fn bar(s: S) {
            S::foo(&s, true, 1);
        }
    """, preview = null)

    fun `test add multiple additional parameters forward with normalizable associated types`() = checkFixByText("<html>Change signature to foo(i32, <b>bool</b>, i32, <b>i32</b>)</html>", """
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = i32; }

        fn foo(a: <Struct as Trait>::Item, b: <Struct as Trait>::Item) {}

        fn main() {
            foo<error descr="This function takes 2 parameters but 4 parameters were supplied [E0061]">(0, false, <error>3/*caret*/</error>, <error>4</error>)</error>;
            foo(0, 1);
        }
    """, """
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = i32; }

        fn foo(a: <Struct as Trait>::Item, b0: bool, b: <Struct as Trait>::Item, i: i32) {}

        fn main() {
            foo(0, false, 3, 4);
            foo(0, , 1, );
        }
    """, preview = null)
}
