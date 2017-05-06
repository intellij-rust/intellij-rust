package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.RsPsiPattern

class RsPsiPatternTest : RsTestBase() {
    fun testOnStructAttr() = testPattern("""
        #[foo]
        //^
        struct Bar;
    """, RsPsiPattern.onStruct)

    fun testOnTraitAttr() = testPattern("""
        #[foo]
        //^
        trait Foo {}
    """, RsPsiPattern.onTrait)

    fun testOnFnAttr() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RsPsiPattern.onFn)

    fun testOnTupleStructAttr() = testPattern("""
        #[foo]
        //^
        struct Bar(u8, u8);
    """, RsPsiPattern.onTupleStruct)

    fun testOnAnyItemStructAttr() = testPattern("""
        #[foo]
        //^
        struct Bar {
            baz: u8
        }
    """, RsPsiPattern.onAnyItem)

    fun testOnAnyItemStaticAttr() = testPattern("""
        #[foo]
        //^
        static BAR: u8 = 1;
    """, RsPsiPattern.onAnyItem)

    fun testOnAnyItemEnumAttr() = testPattern("""
        #[foo]
        //^
        enum Bar {
            Baz,
            Bat
        }
    """, RsPsiPattern.onAnyItem)

    fun testOnAnyItemFnAttr() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RsPsiPattern.onAnyItem)

    fun testOnAnyItemModAttr() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RsPsiPattern.onAnyItem)

    fun testOnAnyItemTraitAttr() = testPattern("""
        #[foo]
        //^
        trait Bar {
        }
    """, RsPsiPattern.onAnyItem)

    fun testOnAnyItemCrateAttr() = testPattern("""
        #![foo]
         //^
    """, RsPsiPattern.onAnyItem)

    fun testOnCrateAttr() = testPattern("""
        #![foo]
         //^
        struct Foo(u8, u8);
    """, RsPsiPattern.onCrate)

    fun testOnDropFnAttr() = testPattern("""
        struct HasDrop;

        impl Drop for HasDrop {
            #[foo]
            //^
            fn drop(&mut self) {
                println!("Dropping!");
            }
        }
    """, RsPsiPattern.onDropFn)

    fun testOnEnumAttr() = testPattern("""
        #[foo]
        //^
        enum Foo {
            Bar,
            Baz
        }
    """, RsPsiPattern.onEnum)

    fun testOnExternBlockAttr() = testPattern("""
        #[foo]
        //^
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, RsPsiPattern.onExternBlock)

    fun testOnExternBlockDeclAttr() = testPattern("""
        extern {
            #[foo]
            //^
            fn bar(baz: size_t) -> size_t;
        }
    """, RsPsiPattern.onExternBlockDecl)

    fun testOnExternCrateAttr() = testPattern("""
        #[foo]
        //^
        extern crate bar;
    """, RsPsiPattern.onExternCrate)

    fun testOnMacroAttr() = testPattern("""
        #[foo]
        //^
        macro_rules! bar {
        }
    """, RsPsiPattern.onMacro)

    fun testOnModAttr() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RsPsiPattern.onMod)

    fun testOnStaticAttr() = testPattern("""
        #[foo]
        //^
        static BAR: u8 = 5;
    """, RsPsiPattern.onStatic)

    fun testOnStaticMutAttr() = testPattern("""
        #[foo]
        //^
        static mut BAR: u8 = 5;
    """, RsPsiPattern.onStaticMut)

    fun testOnTestFnAttr() = testPattern("""
        #[test]
        #[foo]
        //^
        fn test_bar() {

        }
    """, RsPsiPattern.onTestFn)

    fun testOnStmtBeginning() = testPattern("""
        //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningWithinMod() = testPattern("""
        mod foo {    }
                //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningAfterOtherStmt() = testPattern("""
        extern crate foo;
                       //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningAfterBlock() = testPattern("""
        mod foo {}
                //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningIgnoresComments() = testPattern("""
        const A: u8 = 3; /* three */    /* it's greater than two */
                                    //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningNegativeWhenFollowsVisible() = testPatternNegative("""
        abc
          //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningNegativeInMiddleOfOtherStmt() = testPatternNegative("""
        mod abc {}
             //^
    """, RsPsiPattern.onStatementBeginning)

    fun testOnStmtBeginningWithWords() = testPattern("""
        word2
            //^
    """, RsPsiPattern.onStatementBeginning("word1", "word2"))

    fun testOnStmtBeginningWithWordsNegativeWhenWordDoesntFit() = testPatternNegative("""
        word3
            //^
    """, RsPsiPattern.onStatementBeginning("word1", "word2"))

    fun testOnStmtBeginningWithWordsNegativeAtVeryBeginning() = testPatternNegative("   \n//^", RsPsiPattern.onStatementBeginning("word1"))

    fun testInAnyLoopWithinFor() = testPattern("""
        fn foo() {
            for _ in 1..5 { }
                         //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun testInAnyLoopWithinWhile() = testPattern("""
        fn foo() {
            while true { }
                      //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun testInAnyLoopWithinLoop() = testPattern("""
        fn foo() {
            loop { }
                //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun testInAnyLoopWithinForNestedBlock() = testPattern("""
        fn foo() {
            for _ in 1..5 {{ }}
                          //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun testInAnyLoopNegativeBeforeBlock() = testPatternNegative("""
        fn foo() {
            for _ in 1..5 {}
                     //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun testInAnyLoopNegativeAfterBlock() = testPatternNegative("""
        fn foo() {
            while true {}   // Infinite loop
                       //^
        }
    """, RsPsiPattern.inAnyLoop)

    fun testInAnyLoopNegativeInsideClosure() = testPatternNegative("""
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
