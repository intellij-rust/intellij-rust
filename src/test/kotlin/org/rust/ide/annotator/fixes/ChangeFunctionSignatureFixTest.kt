/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class ChangeFunctionSignatureFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test unavailable with unmatched parameters`() = checkFixIsUnavailable("Add `i32`", """
        fn foo(a: bool) {}

        fn main() {
            foo<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(1, <error>2/*caret*/</error>)</error>;
        }
    """)

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
    """)

    fun `test no parameters add multiple parameters`() = checkFixByText("<html>Change signature of foo(<b>i32</b>, <b>bool</b>)</html>", """
        fn foo() {}

        fn main() {
            foo<error descr="This function takes 0 parameters but 2 parameters were supplied [E0061]">(<error>1/*caret*/</error>, <error>false</error>)</error>;
        }
    """, """
        fn foo(i: i32, x: bool) {}

        fn main() {
            foo(1, false);
        }
    """)

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
    """)

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
    """)

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
    """)

    fun `test add multiple additional parameters forward`() = checkFixByText("<html>Change signature of foo(i32, <b>bool</b>, i32, <b>i32</b>)</html>", """
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
    """)

    fun `test add multiple additional parameters backward`() = checkFixByText("<html>Change signature of foo(<b>i32</b>, <b>bool</b>, i32, i32)</html>", """
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
    """)

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
    """)

    fun `test import argument type`() = checkFixByText("Add `S` as `1st` parameter to function `bar`", """
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
            use S;

            pub fn bar(s: S) {}
        }

        pub struct S;

        fn main() {
            let s = S;
            foo::bar(s);
        }
    """)

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
    """)

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
    """)
}
