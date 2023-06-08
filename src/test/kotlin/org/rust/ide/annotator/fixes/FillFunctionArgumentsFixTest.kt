/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.SkipTestWrapping
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class FillFunctionArgumentsFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test availability range one parameter no arguments`() = checkFixAvailableInSelectionOnly("Fill missing arguments", """
        fn foo(a: u32) {}

        fn main() {
            foo<selection>(<error>)</error></selection>;
        }
    """)

    fun `test availability range multiple parameters single argument`() = checkFixAvailableInSelectionOnly("Fill missing arguments", """
        fn foo(a: u32, b: u32, c: &str) {}

        fn main() {
            foo<selection>(1<error>)</error></selection>;
        }
    """)

    fun `test simple call`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32) {}

        fn main() {
            foo(<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32) {}

        fn main() {
            foo(0);
        }
    """)

    fun `test multiple arguments`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: u32, c: &str) {}

        fn main() {
            foo(1<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: u32, c: &str) {}

        fn main() {
            foo(1, 0, "");
        }
    """)

    fun `test ufcs self value`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(self, a: u32) {}
        }
        fn foo() {
            S::foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(self, a: u32) {}
        }
        fn foo() {
            S::foo(S, 0);
        }
    """)

    fun `test ufcs self ref`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(&self, a: u32) {}
        }
        fn foo() {
            S::foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&self, a: u32) {}
        }
        fn foo() {
            S::foo(&S, 0);
        }
    """)

    fun `test ufcs self mut ref`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(&mut self, a: u32) {}
        }
        fn foo() {
            S::foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut self, a: u32) {}
        }
        fn foo() {
            S::foo(&mut S, 0);
        }
    """)

    fun `test ufcs generic trait bound`() = checkFixByText("Fill missing arguments", """
        trait Trait {
            fn foo(&mut self, a: u32) {}
        }
        fn foo<T: Trait>() {
            T::foo(<error>/*caret*/)</error>;
        }
    """, """
        trait Trait {
            fn foo(&mut self, a: u32) {}
        }
        fn foo<T: Trait>() {
            T::foo(&mut (), 0);
        }
    """)

    fun `test method call`() = checkFixByText("Fill missing arguments", """
        struct S;
        impl S {
            fn foo(&self, a: u32, b: u32) {}
        }
        fn foo(s: S) {
            s.foo(1<error>/*caret*/)</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo(&self, a: u32, b: u32) {}
        }
        fn foo(s: S) {
            s.foo(1, 0);
        }
    """)

    fun `test tuple struct`() = checkFixByText("Fill missing arguments", """
        struct S(u32, u32);
        fn foo() {
            S(<error>/*caret*/)</error>;
        }
    """, """
        struct S(u32, u32);
        fn foo() {
            S(0, 0);
        }
    """)

    fun `test closure known parameter`() = checkFixByText("Fill missing arguments", """
        fn main() {
            let closure = |x: i32| (x);
            closure(<error>/*caret*/)</error>;
        }
    """, """
        fn main() {
            let closure = |x: i32| (x);
            closure(0);
        }
    """)

    fun `test closure unknown parameter`() = checkFixByText("Fill missing arguments", """
        fn main() {
            let closure = |x| (x);
            closure(<error>/*caret*/)</error>;
        }
    """, """
        fn main() {
            let closure = |x| (x);
            closure(());
        }
    """)

    fun `test generic parameter turbofish`() = checkFixByText("Fill missing arguments", """
        fn foo<T>(a: T) {}
        fn main() {
            foo::<bool>(<error>/*caret*/)</error>;
        }
    """, """
        fn foo<T>(a: T) {}
        fn main() {
            foo::<bool>(false);
        }
    """)

    fun `test generic parameter method call`() = checkFixByText("Fill missing arguments", """
        struct S<T>(T);

        impl<R> S<R> {
            fn foo(&self, a: R) {}
        }
        fn foo(s: S<u32>) {
            s.foo(<error>/*caret*/)</error>;
        }
    """, """
        struct S<T>(T);

        impl<R> S<R> {
            fn foo(&self, a: R) {}
        }
        fn foo(s: S<u32>) {
            s.foo(0);
        }
    """)

    fun `test trailing comma`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: u32) {}
        fn main() {
            foo(0,<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: u32) {}
        fn main() {
            foo(0, 0);
        }
    """)

    @SkipTestWrapping // TODO better syntax recovery for function arguments
    fun `test empty argument in the middle`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: &str, c: u32) {}
        fn main() {
            foo(0,<error>,</error> 2<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: &str, c: u32) {}
        fn main() {
            foo(0, "", 2);
        }
    """)

    @SkipTestWrapping // TODO better syntax recovery for function arguments
    fun `test empty arguments in the middle`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(0,<error>,</error>,2<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(0, "", false, 2);
        }
    """)

    fun `test empty arguments at the beginning`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(<error>,</error>,true<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(0, "", true, 0);
        }
    """)

    @SkipTestWrapping // TODO better syntax recovery for function arguments
    fun `test empty arguments at the end 1`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(1,<error>,</error>,<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(1, "", false, 0);
        }
    """)

    @SkipTestWrapping // TODO better syntax recovery for function arguments
    fun `test empty arguments at the end 2`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(1,<error>,</error><error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(1, "", false, 0);
        }
    """)

    @SkipTestWrapping // TODO better syntax recovery for function arguments
    fun `test interleaved empty arguments`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(<error>,</error>"",<error>,</error> 2<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32, b: &str, c: bool, d: u32) {}
        fn main() {
            foo(0, "", false, 2);
        }
    """)

    fun `test too many empty arguments`() = checkFixByText("Fill missing arguments", """
        fn foo(a: u32, b: u32) {}
        fn main() {
            foo(1,<error>,</error>,,,/*caret*/<error>)</error>;
        }
    """, """
        fn foo(a: u32, b: u32) {}
        fn main() {
            foo(1, 0);
        }
    """)

    fun `test correct number of arguments trailing comma`() = checkFixIsUnavailable("Fill missing arguments", """
        fn foo(a: u32, b: u32) {}
        fn main() {
            foo(0, 0,/*caret*/);
        }
    """)

    fun `test correct number of arguments too many trailing commas`() = checkFixIsUnavailable("Fill missing arguments", """
        fn foo(a: u32, b: u32) {}
        fn main() {
            foo(0, 0,<error>,</error>,/*caret*/);
        }
    """)
}
