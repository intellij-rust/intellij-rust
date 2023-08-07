/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.SkipTestWrapping
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsSyntaxErrorsAnnotator

class AddTypeFixTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test const`() = checkFixByText("Add type i32", """
        const <error>CONST/*caret*/</error> = 1;
    """, """
        const CONST: i32/*caret*/ = 1;
    """)

    fun `test static`() = checkFixByText("Add type i32", """
        static <error>STATIC/*caret*/</error> = 1;
    """, """
        static STATIC: i32/*caret*/ = 1;
    """)

    fun `test unknown type`() = checkFixByText("Add type _", """
        const <error>CONST/*caret*/</error> = S;
    """, """
        const CONST: _/*caret*/ = S;
    """)

    fun `test partially unknown type`() = checkFixByText("Add type (_, _)", """
        const <error>CONST/*caret*/</error> = (S, S);
    """, """
        const CONST: (_, _)/*caret*/ = (S, S);
    """)

    fun `test alias`() = checkFixByText("Add type Foo", """
        type Foo = u32;

        const fn foo() -> Foo { 0 }

        const <error>CONST/*caret*/</error> = foo();
    """, """
        type Foo = u32;

        const fn foo() -> Foo { 0 }

        const CONST: Foo/*caret*/ = foo();
    """)

    fun `test skip default type argument`() = checkFixByText("Add type Foo", """
        struct Foo<T = u32>(T);

        const <error>CONST/*caret*/</error> = Foo(0u32);
    """, """
        struct Foo<T = u32>(T);

        const CONST: Foo = Foo(0u32);
    """)

    @SkipTestWrapping
    fun `test missing expr`() = checkFixIsUnavailable("Add type", """
        <error>const <error>CONST/*caret*/</error>;</error>
    """)
}
