/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.RsPsiPattern

class RsPsiPatternTest : RsTestBase() {
    fun `test on struct attr`() = testPattern("""
        #[foo]
        //^
        struct Bar;
    """, RsPsiPattern.onStruct)

    fun `test on trait attr`() = testPattern("""
        #[foo]
        //^
        trait Foo {}
    """, RsPsiPattern.onTrait)

    fun `test on fn attr`() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RsPsiPattern.onFn)

    fun `test on tuple struct attr`() = testPattern("""
        #[foo]
        //^
        struct Bar(u8, u8);
    """, RsPsiPattern.onTupleStruct)

    fun `test on any item struct attr`() = testPattern("""
        #[foo]
        //^
        struct Bar {
            baz: u8
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item static attr`() = testPattern("""
        #[foo]
        //^
        static BAR: u8 = 1;
    """, RsPsiPattern.onAnyItem)

    fun `test on any item enum attr`() = testPattern("""
        #[foo]
        //^
        enum Bar {
            Baz,
            Bat
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item fn attr`() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item mod attr`() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item trait attr`() = testPattern("""
        #[foo]
        //^
        trait Bar {
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item crate attr`() = testPattern("""
        #![foo]
         //^
    """, RsPsiPattern.onAnyItem)

    fun `test on crate attr`() = testPattern("""
        #![foo]
         //^
        struct Foo(u8, u8);
    """, RsPsiPattern.onCrate)

    fun `test on drop fn attr`() = testPattern("""
        struct HasDrop;

        impl Drop for HasDrop {
            #[foo]
            //^
            fn drop(&mut self) {
                println!("Dropping!");
            }
        }
    """, RsPsiPattern.onDropFn)

    fun `test on enum attr`() = testPattern("""
        #[foo]
        //^
        enum Foo {
            Bar,
            Baz
        }
    """, RsPsiPattern.onEnum)

    fun `test on extern block attr`() = testPattern("""
        #[foo]
        //^
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, RsPsiPattern.onExternBlock)

    fun `test on extern block decl attr`() = testPattern("""
        extern {
            #[foo]
            //^
            fn bar(baz: size_t) -> size_t;
        }
    """, RsPsiPattern.onExternBlockDecl)

    fun `test on extern crate attr`() = testPattern("""
        #[foo]
        //^
        extern crate bar;
    """, RsPsiPattern.onExternCrate)

    fun `test onMacroDefinition`() = testPattern("""
        #[foo]
        //^
        macro_rules! bar {
        }
    """, RsPsiPattern.onMacroDefinition)

    fun `test on mod attr`() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RsPsiPattern.onMod)

    fun `test on mod attr 2`() = testPattern("""
        #[foo]
        //^
        mod bar;
    """, RsPsiPattern.onMod)

    fun `test on static attr`() = testPattern("""
        #[foo]
        //^
        static BAR: u8 = 5;
    """, RsPsiPattern.onStatic)

    fun `test on static mut attr`() = testPattern("""
        #[foo]
        //^
        static mut BAR: u8 = 5;
    """, RsPsiPattern.onStaticMut)

    fun `test on test fn attr`() = testPattern("""
        #[test]
        #[foo]
        //^
        fn test_bar() {

        }
    """, RsPsiPattern.onTestFn)

    fun `test on stmt beginning`() = testPattern("""
        //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning within mod`() = testPattern("""
        mod foo {    }
                //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning after other stmt`() = testPattern("""
        extern crate foo;
                       //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning after block`() = testPattern("""
        mod foo {}
                //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning ignores comments`() = testPattern("""
        const A: u8 = 3; /* three */    /* it's greater than two */
                                    //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning negative when follows visible`() = testPatternNegative("""
        abc
          //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning negative in middle of other stmt`() = testPatternNegative("""
        mod abc {}
             //^
    """, RsPsiPattern.onStatementBeginning)

    fun `test on stmt beginning with words`() = testPattern("""
        word2
            //^
    """, RsPsiPattern.onStatementBeginning("word1", "word2"))

    fun `test on stmt beginning with words negative when word doesnt fit`() = testPatternNegative("""
        word3
            //^
    """, RsPsiPattern.onStatementBeginning("word1", "word2"))

    fun `test on stmt beginning with words negative at very beginning`() = testPatternNegative("   \n//^", RsPsiPattern.onStatementBeginning("word1"))

    fun `test in any loop within for`() = testPattern("""
        fn foo() {
            for _ in 1..5 { }
                         //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun `test in any loop within while`() = testPattern("""
        fn foo() {
            while true { }
                      //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun `test in any loop within loop`() = testPattern("""
        fn foo() {
            loop { }
                //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun `test in any loop within for nested block`() = testPattern("""
        fn foo() {
            for _ in 1..5 {{ }}
                          //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun `test in any loop negative before block`() = testPatternNegative("""
        fn foo() {
            for _ in 1..5 {}
                     //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun `test in any loop negative after block`() = testPatternNegative("""
        fn foo() {
            while true {}   // Infinite loop
                       //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun `test in any loop negative inside closure`() = testPatternNegative("""
        fn foo() {
            while true { let _ = |x| { x + 1 }; }   // Infinite loop
                                       //^
        }
    """, RsPsiPattern.inAnyLoop)

    private fun <T> testPattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        InlineFile(code)
        val element = findElementInEditor<PsiElement>()
        assertTrue(pattern.accepts(element))
    }

    private fun <T> testPatternNegative(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        InlineFile(code)
        val element = findElementInEditor<PsiElement>()
        assertFalse(pattern.accepts(element, null))
    }
}
