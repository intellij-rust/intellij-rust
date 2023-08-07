/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class ConvertClosureToFunctionIntentionTest : RsIntentionTestBase(ConvertClosureToFunctionIntention::class) {

    fun `test intention is only available on variable name and argument list`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>let foo = |x: i32| -> i32</selection> { x + 1 };
        }
    """)

    fun `test conversion from closure to function`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test conversion auto-generates function name for wildcard binding`() = doAvailableTest("""
        fn main() {
            let _ = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """, """
        fn main() {
            fn func/*caret*/(x: i32) -> i32 { x + 1 }
        }
    """)

    fun `test intention adds function body when closure doesnt have one`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| -> i32 x + 1;
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test intention adds return type to function when closure doesnt have one`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| x + 1;
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test intention does not add unit return type explicitly`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| println!(x);
        }
    """, """
        fn main() {
            fn foo(x: i32) { println!(x) }/*caret*/
        }
    """)

    fun `test raw identifier`() = doAvailableTest("""
        fn main() {
            let r#match = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """, """
        fn main() {
            fn r#match(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test inferred types`() = doAvailableTest("""
        fn main() {
            let foo: fn(i32) -> i32 = |x/*caret*/| { x + 1 };
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test keep aliases`() = doAvailableTest("""
        type Foo = (u32, u32);

        fn main() {
            let foo = |x: Foo/*caret*/| { x };
        }
    """, """
        type Foo = (u32, u32);

        fn main() {
            fn foo(x: Foo) -> Foo { x }/*caret*/
        }
    """)

    fun `test skip default type argument`() = doAvailableTest("""
        struct S<T = u32>(T);

        fn main() {
            let foo = |s: S/*caret*/| { s };
        }
    """, """
        struct S<T = u32>(T);

        fn main() {
            fn foo(s: S) -> S { s }/*caret*/
        }
    """)

    fun `test infer generic type`() = doAvailableTest("""
        struct Wrap<T>(T);

        fn main() {
            let /*caret*/foo = |x: Wrap<_>| x;
            let _: Wrap<i8> = foo(Wrap(3_i8));
        }
    """, """
        struct Wrap<T>(T);

        fn main() {
            fn foo(x: Wrap<i8>) -> Wrap<i8> { x }
            let _: Wrap<i8> = foo(Wrap(3_i8));
        }
    """)
}
