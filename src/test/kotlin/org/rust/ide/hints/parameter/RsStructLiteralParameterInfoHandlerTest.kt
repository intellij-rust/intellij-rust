/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import org.rust.ide.hints.parameter.RsStructLiteralParameterInfoHandler.Description
import org.rust.lang.core.psi.RsStructLiteralBody

class RsStructLiteralParameterInfoHandlerTest
    : RsParameterInfoHandlerTestBase<RsStructLiteralBody, Description>(RsStructLiteralParameterInfoHandler()) {

    fun `test no fields`() = checkByText("""
        struct Foo {}
        fn main() {
            Foo { /*caret*/ };
        }
    """, "<no fields>", 0)

    fun `test one field, zero present`() = checkByText("""
        struct Foo { a: i32 }
        fn main() {
            Foo { /*caret*/ };
        }
    """, "a: i32", 0)

    fun `test one field, one present`() = checkByText("""
        struct Foo { a: i32 }
        fn main() {
            Foo { a: 1/*caret*/ };
        }
    """, "a: i32", 0)

    fun `test two fields, first present`() = checkByText("""
        struct Foo { a: i32, b: i32 }
        fn main() {
            Foo { a: 1/*caret*/ };
        }
    """, "a: i32, b: i32", 0)

    fun `test two fields, second present 1`() = checkByText("""
        struct Foo { a: i32, b: i32 }
        fn main() {
            Foo { /*caret*/b: 1 };
        }
    """, "a: i32, b: i32", 1)

    fun `test two fields, second present 2`() = checkByText("""
        struct Foo { a: i32, b: i32 }
        fn main() {
            Foo { b:/*caret*/ 1 };
        }
    """, "a: i32, b: i32", 1)

    fun `test two fields, second present 3`() = checkByText("""
        struct Foo { a: i32, b: i32 }
        fn main() {
            Foo { b: 1/*caret*/ };
        }
    """, "a: i32, b: i32", 1)

    fun `test three fields, first and third present`() = checkByText("""
        struct Foo { a: i32, b: i32, c: i32 }
        fn main() {
            Foo { a: 1, c: 1/*caret*/ };
        }
    """, "a: i32, b: i32, c: i32", 2)

    fun `test missing field`() = checkByText("""
        struct Foo { a: i32, b: i32, c: i32 }
        fn main() {
            Foo { a: 1, /*caret*/, c: 1 };
        }
    """, "a: i32, b: i32, c: i32", 1)

    fun `test field without value`() = checkByText("""
        struct Foo { a: i32, b: i32 }
        fn main() {
            Foo { b/*caret*/ };
        }
    """, "a: i32, b: i32", 1)

    fun `test enum variant`() = checkByText("""
        enum E {
            Foo { a: i32, b: i32 },
        }
        fn main() {
            E::Foo { b: 1/*caret*/ };
        }
    """, "a: i32, b: i32", 1)

    fun `test generics`() = checkByText("""
        struct Foo<T> { a: T, b: T }
        fn main() {
            Foo { a: 1, /*caret*/ };
        }
    """, "a: i32, b: i32", 1)

    fun `test generics (enum variant)`() = checkByText("""
        enum E<T> {
            Foo { a: T, b: T },
        }
        fn main() {
            E::Foo { a: 1, /*caret*/ };
        }
    """, "a: i32, b: i32", 1)

    fun `test raw identifier`() = checkByText("""
        struct Foo { r#let: i32 }
        fn main() {
            Foo { /*caret*/ };
        }
    """, "let: i32", 0)
}
