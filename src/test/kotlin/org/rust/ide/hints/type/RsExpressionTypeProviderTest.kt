/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.codeInsight.hint.ShowExpressionTypeHandler
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.openapiext.escaped


class RsExpressionTypeProviderTest : RsTestBase() {
    fun `test expr simple type`() = doTest("""
        fn foo() {
            let x = /*caret*/5u32;
        }
    """, "u32")

    fun `test pat simple type`() = doTest("""
        fn foo() {
            let /*caret*/x = 5u32;
        }
    """, "u32")

    fun `test pat-field simple type`() = doTest("""
        struct S { field: i32 }
        fn foo() {
            let S { /*caret*/field } = S { field: 0 };
        }
    """, "i32, S")

    fun `test pat generic type`() = doTest("""
        struct S<T> { t: T }

        fn foo<T>(c: S<T>) {
            let /*caret*/b = c;
        }
    """, "S<T>")

    fun `test pat complex generic type`() = doTest("""
        struct S<T, U, V=u32> { t: T, u: U, v: V }

        fn foo<T, U>(c: S<T, U>) {
            let /*caret*/b = c;
        }
    """, "S<T, U, u32>")

    fun `test pat ref type`() = doTest("""
        fn foo(c: &mut u32) {
            let /*caret*/b = c;
        }
    """, "&mut u32")

    fun `test pat associated type`() = doTest("""
        trait Trait {
            type Item = ();
        }

        fn foo(c: dyn Trait<Item=u32>) {
            let /*caret*/b = c;
        }
    """, "dyn Trait<Item=u32>")

    fun `test pat associated generic type`() = doTest("""
        trait Trait {
            type Item = ();
        }

        fn foo<T>(c: dyn Trait<Item=T>) {
            let /*caret*/b = c;
        }
    """, "dyn Trait<Item=T>")

    fun `test pat aliased type`() = doTest("""
        struct S<T> { t: T }

        type BoxedS<T> = S<T>;

        fn foo<T>(c: &BoxedS<T>) {
            let /*caret*/b = c;
        }
    """, "&BoxedS<T>")

    fun `test complex expr`() = doTest("""
        #[lang = "add"]
        pub trait Add<Rhs = Self> { type Output; }

        fn foo() {
            let x = /*caret*/2 + 2 + 2;
        }
    """, "i32, i32, i32")

    fun `test expr in a macro call`() = doTest("""
        macro_rules! foo {
            ($ e:expr) => { let a = $ e; };
        }
        fn foo() {
            foo!(/*caret*/2);
        }
    """, "i32")

    fun `test complex expr in a macro call`() = doTest("""
        #[lang = "add"]
        pub trait Add<Rhs = Self> { type Output; }

        macro_rules! foo {
            ($ e:expr) => { let a = $ e; };
        }
        fn foo() {
            foo!(/*caret*/2 + 2 + 2);
        }
    """, "i32, i32, i32")

    fun `test no type info in macro call if a part of an expr is passed`() = doTest("""
        macro_rules! foo {
            ($ t:tt) => { let a = m::$ t; };
        }
        mod m {
            pub const C: i32 = 1;
        }
        fn foo() {
            foo!(/*caret*/C);
        }
    """, "")

    fun `test complex expression in macro call constructed from separate tokens 1`() = doTest("""
        #[lang = "add"]
        pub trait Add<Rhs = Self> { type Output; }

        macro_rules! foo {
            ($($ t:tt)*) => { let a = $($ t)*; };
        }
        fn foo() {
            foo!(/*caret*/2 + 2 + 2);
        }
    """, "i32, i32, i32")

    private fun doTest(@Language("Rust") code: String, type: String) {
        InlineFile(code).withCaret()

        val expressions = ShowExpressionTypeHandler(false)
            .getExpressions(myFixture.file, myFixture.editor)
            .entries
            .filter { it.value is RsExpressionTypeProvider }

        assertEquals(type.escaped, expressions.joinToString(", ") { it.value.getInformationHint(it.key) })
    }
}
