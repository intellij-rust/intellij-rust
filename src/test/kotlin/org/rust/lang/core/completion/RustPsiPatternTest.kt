package org.rust.lang.core.completion

import RustPsiPattern
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

class RustPsiPatternTest : RustTestCaseBase() {
    override val dataPath: String get() = ""

    fun testOnStructAttr() = testPattern("""
        #[foo]
        //^
        struct Bar;
    """, RustPsiPattern.onStruct)

    fun testOnTraitAttr() = testPattern("""
        #[foo]
        //^
        trait Foo {}
    """, RustPsiPattern.onTrait)

    fun testOnFnAttr() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RustPsiPattern.onFn)

    fun testOnTupleStructAttr() = testPattern("""
        #[foo]
        //^
        struct Bar(u8, u8);
    """, RustPsiPattern.onTupleStruct)

    fun testOnAnyItemStructAttr() = testPattern("""
        #[foo]
        //^
        struct Bar {
            baz: u8
        }
    """, RustPsiPattern.onAnyItem)

    fun testOnAnyItemStaticAttr() = testPattern("""
        #[foo]
        //^
        static BAR: u8 = 1;
    """, RustPsiPattern.onAnyItem)

    fun testOnAnyItemEnumAttr() = testPattern("""
        #[foo]
        //^
        enum Bar {
            Baz,
            Bat
        }
    """, RustPsiPattern.onAnyItem)

    fun testOnAnyItemFnAttr() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RustPsiPattern.onAnyItem)

    fun testOnAnyItemModAttr() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RustPsiPattern.onAnyItem)

    fun testOnAnyItemTraitAttr() = testPattern("""
        #[foo]
        //^
        trait Bar {
        }
    """, RustPsiPattern.onAnyItem)

    fun testOnAnyItemCrateAttr() = testPattern("""
        #![foo]
         //^
    """, RustPsiPattern.onAnyItem)

    fun testOnCrateAttr() = testPattern("""
        #![foo]
         //^
        struct Foo(u8, u8);
    """, RustPsiPattern.onCrate)

    fun testOnDropFnAttr() = testPattern("""
        struct HasDrop;

        impl Drop for HasDrop {
            #[foo]
            //^
            fn drop(&mut self) {
                println!("Dropping!");
            }
        }
    """, RustPsiPattern.onDropFn)

    fun testOnEnumAttr() = testPattern("""
        #[foo]
        //^
        enum Foo {
            Bar,
            Baz
        }
    """, RustPsiPattern.onEnum)

    fun testOnExternBlockAttr() = testPattern("""
        #[foo]
        //^
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, RustPsiPattern.onExternBlock)

    fun testOnExternBlockDeclAttr() = testPattern("""
        extern {
            #[foo]
            //^
            fn bar(baz: size_t) -> size_t;
        }
    """, RustPsiPattern.onExternBlockDecl)

    fun testOnExternCrateAttr() = testPattern("""
        #[foo]
        //^
        extern crate bar;
    """, RustPsiPattern.onExternCrate)

    fun testOnMacroAttr() = testPattern("""
        #[foo]
        //^
        macro_rules! bar {
        }
    """, RustPsiPattern.onMacro)

    fun testOnModAttr() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RustPsiPattern.onMod)

    fun testOnStaticAttr() = testPattern("""
        #[foo]
        //^
        static BAR: u8 = 5;
    """, RustPsiPattern.onStatic)

    fun testOnStaticMutAttr() = testPattern("""
        #[foo]
        //^
        static mut BAR: u8 = 5;
    """, RustPsiPattern.onStaticMut)

    fun testOnTestFnAttr() = testPattern("""
        #[test]
        #[foo]
        //^
        fn test_bar() {

        }
    """, RustPsiPattern.onTestFn)

    fun testOnStmtBeginning() = testPattern("""
        //^
    """, RustPsiPattern.OnStatementBeginning())

    fun testOnStmtBeginningWithinMod() = testPattern("""
        mod foo {    }
                //^
    """, RustPsiPattern.OnStatementBeginning())

    fun testOnStmtBeginningAfterOtherStmt() = testPattern("""
        extern crate foo;
                       //^
    """, RustPsiPattern.OnStatementBeginning())

    fun testOnStmtBeginningAfterBlock() = testPattern("""
        mod foo {}
                //^
    """, RustPsiPattern.OnStatementBeginning())

    fun testOnStmtBeginningIgnoresComments() = testPattern("""
        const A: u8 = 3; /* three */    /* it's greater than two */
                                    //^
    """, RustPsiPattern.OnStatementBeginning())

    fun testOnStmtBeginningNegativeWhenFollowsVisible() = testPatternNegative("""
        abc
          //^
    """, RustPsiPattern.OnStatementBeginning())

    fun testOnStmtBeginningNegativeInMiddleOfOtherStmt() = testPatternNegative("""
        mod abc {}
             //^
    """, RustPsiPattern.OnStatementBeginning())

    private fun <T> testPattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        InlineFile(code)
        val element = findElementInEditor<PsiElement>()
        assertTrue(pattern.accepts(element))
    }

    private fun testPattern(@Language("Rust") code: String, pattern: PatternCondition<PsiElement>) {
        InlineFile(code)
        val element = findElementInEditor<PsiElement>()
        assertTrue(pattern.accepts(element, null))
    }

    private fun testPatternNegative(@Language("Rust") code: String, pattern: PatternCondition<PsiElement>) {
        InlineFile(code)
        val element = findElementInEditor<PsiElement>()
        assertFalse(pattern.accepts(element, null))
    }
}
