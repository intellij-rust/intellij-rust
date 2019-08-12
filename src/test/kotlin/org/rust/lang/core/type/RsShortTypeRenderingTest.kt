/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.presentation.shortPresentableText
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.type

class RsShortTypeRenderingTest : RsTestBase() {
    fun `test ignore ref for levels`() = testShortTypeExpr("""
        struct S;
        fn main() {
            let s = &&&&S;
            s;
          //^ &&&&S
        }
    """)

    fun `test basic test`() = testShortTypeExpr("""
        struct S<T, U>;

        impl<T, U> S<T, U> {
            fn wrap<F>(self, f: F) -> S<F, Self> {
                unimplemented!()
            }
        }

        fn main() {
            let s: S<(), ()> = unimplemented!();
            let foo = s
                .wrap(|x: i32| x)
                .wrap(|x: i32| x)
                .wrap(|x: i32| x)
                .wrap(|x: i32| x);
            foo;
            //^ S<fn(i32) -> i32, S<fn(i32) -> i32, S<…, …>>>
        }
    """)

    fun `test long 2-level type`() = testShortTypeExpr("""
        struct S<A, B, C, D>;
        struct SomeLongNamedType;

        fn main() {
            let s: S<SomeLongNamedType, SomeLongNamedType, SomeLongNamedType, SomeLongNamedType> = unimplemented!();
            s;
        } //^ S<…, …, …, …>
    """)

    // TODO write more simple tests

    fun `test unknown type`() = testShortTypeExpr("""
        struct S<T>(T);
        fn main() {
            let s = S(UnknownType);
            s;
        } //^ S<?>
    """)

    fun `test aliased type`() = testShortTypeExpr("""
        struct S<A, B>(A, B);
        type Foo<T> = S<T, u8>;
        fn foo(s: Foo<i32>) {
            s;
        } //^ Foo<i32>
    """)

    private fun testShortTypeExpr(@Language("Rust") code: String) {
        InlineFile(code)
        val (expr, expectedType) = findElementAndDataInEditor<RsExpr>()
        val actualType = expr.type.shortPresentableText
        assertEquals(expectedType, actualType)
    }
}
