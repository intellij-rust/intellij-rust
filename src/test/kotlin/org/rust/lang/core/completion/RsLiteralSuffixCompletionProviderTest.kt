/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger

class RsLiteralSuffixCompletionProviderTest : RsCompletionTestBase() {

    fun `test i32 suffix`() = checkCompletion("123i32", """
        fn main() {
            let a = 123i/*caret*/
        }
    """, """
        fn main() {
            let a = 123i32/*caret*/
        }
    """)

    fun `test i64 suffix`() = checkCompletion("1i64", """
        fn main() {
            let a = 1i6/*caret*/
        }
    """, """
        fn main() {
            let a = 1i64/*caret*/
        }
    """)

    fun `test u32 suffix before semicolon`() = checkCompletion("5u32", """
        fn main() {
            let a = 5u/*caret*/;
        }
    """, """
        fn main() {
            let a = 5u32/*caret*/;
        }
    """)

    fun `test usize suffix negative number`() = checkCompletion("-1usize", """
        fn main() {
            let a = -1us/*caret*/
        }
    """, """
        fn main() {
            let a = -1usize/*caret*/
        }
    """)

    fun `test isize suffix hex number`() = checkCompletion("0xffisize", """
        fn main() {
            let a = 0xffi/*caret*/
        }
    """, """
        fn main() {
            let a = 0xffisize/*caret*/
        }
    """)

    fun `test f64 suffix`() = checkCompletion("1.0f64", """
        fn main() {
            let a = 1.0f/*caret*/
        }
    """, """
        fn main() {
            let a = 1.0f64/*caret*/
        }
    """)

    fun `test trait constant`() = checkCompletion("0u8", """
        trait Foo {
            const FOO: u8;
        }

        impl Foo for () {
            const FOO: u8 = 0u/*caret*/;
        }
    """, """
        trait Foo {
            const FOO: u8;
        }

        impl Foo for () {
            const FOO: u8 = 0u8/*caret*/;
        }
    """)

    fun `test no completion if no suffix`() = checkNotContainsCompletion(possibleCompletions("1"), """
        fn main() {
            let a = 1/*caret*/
        }
    """)

    fun `test no completion for string literal`() = checkNotContainsCompletion(possibleCompletions("\"\""), """
        fn main() {
            let a = ""/*caret*/
        }
    """)

    fun `test no completion for boolean literal`() = checkNotContainsCompletion(possibleCompletions("true"), """
        fn main() {
            let a = true/*caret*/
        }
    """)

    fun `test no completion for char literal`() = checkNotContainsCompletion(possibleCompletions("'c'"), """
        fn main() {
            let a = 'c'/*caret*/
        }
    """)

    fun `test no usize completion for i suffix`() = checkNotContainsCompletion("1usize", """
        fn main() {
            let a = 1i/*caret*/
        }
    """)

    fun `test no completion if in variable name`() = checkNotContainsCompletion(possibleCompletions("1"), """
        fn main() {
            let 1/*caret*/ = 5
        }
    """)

    fun `test no completion if in struct field`() = checkNotContainsCompletion(possibleCompletions("1"), """
        struct S {
            1/*caret*/
        }
    """)

    fun `test no completion for octal literal and float suffix`() = checkNotContainsCompletion("0o3f32", """
        fn main() {
            let a = 0o3f/*caret*/
        }
    """)

    fun `test no completion for float literal and integer suffix`() = checkNotContainsCompletion("1.0i64", """
        fn main() {
            let a = 1.0i/*caret*/
        }
    """)

    fun `test completion for scientific notation and float suffix`() = checkContainsCompletion("1e5f32", """
        fn main() {
            let a = 1e5f/*caret*/
        }
    """)

    fun `test no completion for scientific notation and integer suffix`() = checkNotContainsCompletion("1e5i32", """
        fn main() {
            let a = 1e5i/*caret*/
        }
    """)

    fun `test no completion for already existing integer suffix`() = checkNotContainsCompletion(possibleCompletions("1i32"), """
        fn main() {
            let a = 1i32i/*caret*/
        }
    """)

    fun `test no completion for already existing float suffix`() = checkNotContainsCompletion(possibleCompletions("1.0f64"), """
        fn main() {
            let a = 1.0f64f/*caret*/
        }
    """)

    private fun possibleCompletions(prefix: String): List<String> {
        val suffixes = TyInteger.NAMES + TyFloat.NAMES
        return suffixes + suffixes.map { "$prefix$it" }
    }
}
