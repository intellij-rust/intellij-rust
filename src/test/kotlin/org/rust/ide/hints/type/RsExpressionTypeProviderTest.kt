/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.escaped


class RsExpressionTypeProviderTest : RsTestBase() {
    fun `test simple type`() = doTest("""
        fn foo() {
            let /*caret*/x = 5u32;
        }
    """, "u32")

    fun `test generic type`() = doTest("""
        struct S<T> { t: T }

        fn foo<T>(c: S<T>) {
            let /*caret*/b = c;
        }
    """, "S<T>")

    fun `test complex generic type`() = doTest("""
        struct S<T, U, V=u32> { t: T, u: U, v: V }

        fn foo<T, U>(c: S<T, U>) {
            let /*caret*/b = c;
        }
    """, "S<T, U, u32>")

    fun `test ref type`() = doTest("""
        fn foo(c: &mut u32) {
            let /*caret*/b = c;
        }
    """, "&mut u32")

    fun `test associated type`() = doTest("""
        trait Trait {
            type Item = ();
        }

        fn foo(c: dyn Trait<Item=u32>) {
            let /*caret*/b = c;
        }
    """, "dyn Trait<Item=u32>")

    fun `test associated generic type`() = doTest("""
        trait Trait {
            type Item = ();
        }

        fn foo<T>(c: dyn Trait<Item=T>) {
            let /*caret*/b = c;
        }
    """, "dyn Trait<Item=T>")

    fun `test aliased type`() = doTest("""
        struct S<T> { t: T }

        type BoxedS<T> = S<T>;

        fn foo<T>(c: &BoxedS<T>) {
            let /*caret*/b = c;
        }
    """, "&BoxedS<T>")

    fun `test field shorthand`() = doTest("""
        struct S {
            foo: i32,
        }
        fn main() {
            let foo = 1;
            let _ = S {
                /*caret*/foo
            };
        }
    """, "i32, S")

    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attr proc macro`() = doTest("""
        use test_proc_macros::attr_add_to_fn_beginning;

        #[attr_add_to_fn_beginning(let a = 0;)]
        fn main() {
            let _ = /*caret*/a;
        }
    """, "i32")

    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attr proc macro 2`() = doTest("""
        fn foo(x: i32) -> i32 { x }

        #[test_proc_macros::attr_as_is]
        fn main() {
            let _ = /*caret*/foo(0);
        }
    """, "fn(i32) -> i32 {foo}, i32")

    fun `test inside macro call`() = doTest("""
        macro_rules! as_is { ($($ t:tt)*) => { $($ t)* } }

        fn foo() -> i32 { 0 }

        fn main() {
            as_is! {
                /*caret*/foo();
            }
        }
    """, "fn() -> i32 {foo}, i32")

    private fun doTest(@Language("Rust") code: String, type: String) {
        InlineFile(code).withCaret()

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
            ?: error("No element under the caret")

        val provider = RsExpressionTypeProvider()
        val expressions = provider.getExpressionsAt(element)
        assertEquals(type.escaped, expressions.joinToString(", ") { provider.getInformationHint(it) })
    }
}
