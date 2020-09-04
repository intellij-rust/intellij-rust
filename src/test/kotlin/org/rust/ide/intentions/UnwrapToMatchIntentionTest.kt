/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class UnwrapToMatchIntentionTest: RsIntentionTestBase(UnwrapToMatchIntention::class) {

    fun `test option base case`() = doAvailableTest("""
        fn main() {
            let a: Option<i32> = Some(42);
            let a = a.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a: Option<i32> = Some(42);
            let a = match a {
                Some(x) => x,
                None => unimplemented!(),
            };
        }
    """)

    fun `test result base case`() = doAvailableTest("""
        fn main() {
            let a: Result<i32, &str> = Ok(42);
            let a = a.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a: Result<i32, &str> = Ok(42);
            let a = match a {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            };
        }
    """)

    fun `test base case - redundant whitespaces are ignored`() = doAvailableTest("""
        fn main() {
            let a : Option<&str> = None;
            let a = a           .

                unwrap()/*caret*/;
        }
    """, """
        fn main() {
            let a : Option<&str> = None;
            let a = match a {
                Some(x) => x,
                None => unimplemented!(),
            };
        }
    """)

    fun `test chain of dot expessions`() = doAvailableTest("""
        fn main() {
            let a = Test{};
            a.b().unwrap/*caret*/().d().e().f();
        }

        struct Test {}

        impl Test {
            fn b(&self) -> Option<i32> {
                Some(42)
            }
        }
    """, """
        fn main() {
            let a = Test{};
            match a.b() {
                Some(x) => x,
                None => unimplemented!(),
            }.d().e().f();
        }

        struct Test {}

        impl Test {
            fn b(&self) -> Option<i32> {
                Some(42)
            }
        }
        """)

    fun `test unwrap() as method call parameter`() = doAvailableTest("""
        fn main() {
            let b = Some(50);
            f(a, b.unwrap/*caret*/(), c)
        }
    """, """
        fn main() {
            let b = Some(50);
            f(a, match b {
                Some(x) => x,
                None => unimplemented!(),
            }, c)
        }
    """)

    fun `test binary expression with unwrap() result`() = doAvailableTest("""
        fn main() {
            let x: Result<i32, &str> = Err("test");
            let x = x.unwrap/*caret*/() + 42;
        }
    """, """
        fn main() {
            let x: Result<i32, &str> = Err("test");
            let x = match x {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            } + 42;
        }
    """)

    fun `test chain of unwrap()-s`() = doAvailableTest("""
        fn main() {
            let x = Some(Some(Some(42)));
            let x = x.unwrap().unwrap/*caret*/().unwrap();
        }
    """, """
        fn main() {
            let x = Some(Some(Some(42)));
            let x = match x.unwrap() {
                Some(x) => x,
                None => unimplemented!(),
            }.unwrap();
        }
    """)

    fun `test base case - nor Option or Result type being unwrapped`() = doUnavailableTest("""
        fn main() {
            enum Foobar {
                Foo,
                Bar
            }

            let a = Foo;
            a.unwrap/*caret*/();
        }
    """)

    fun `test base case - type cannot be inferred`() = doUnavailableTest("""
        fn main() {
            a.unwrap/*caret*/();
        }
    """)

    fun `test base case - brackets missing`() = doUnavailableTest("""
         fn main() {
            let a = Some(5);
            let a = a.unwrap/*caret*/;
        }
    """)

    fun `test base case - incorrect method call`() = doUnavailableTest("""
         fn main() {
            let a = Some(5);
            let a = a.unwra/*caret*/();
        }
    """)

    fun `test base case - no unwrap() call receiver`() = doUnavailableTest("""
         fn foo(a: Option<i32>) {
            let a = unwrap/*caret*/();
         }
    """)

    fun `test base case - unwrap() call with parameters`() = doUnavailableTest("""
         fn main() {
            let a = Some(5);
            let a = unwrap/*caret*/(0);
        }
    """)

    fun `test base case - call with blank type specialization`() = doUnavailableTest("""
        fn main() {
            let a = Some(5);
            let a = a.unwrap::<>/*caret*/();
        }
    """)

    fun `test base case - call with non-blank type specialization`() = doUnavailableTest("""
        fn main() {
            let a = Some(5);
            let a = a.unwrap::<i32>/*caret*/();
        }
    """)
}
