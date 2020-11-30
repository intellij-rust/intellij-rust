/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.RsPsiPattern

class RsPsiPatternTest : RsTestBase() {
    fun `test on struct attr`() = testAttributePattern("""
        #[foo]
        //^
        struct Bar;
    """, RsPsiPattern.onStruct)

    fun `test on trait attr 1`() = testAttributePattern("""
        #[foo]
        //^
        trait Foo {}
    """, RsPsiPattern.onTrait)

    fun `test on trait attr 2`() = testAttributePattern("""
        trait Foo {
            #![foo]
             //^
        }
    """, RsPsiPattern.onTrait)

    fun `test on fn attr 1`() = testAttributePattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RsPsiPattern.onFn)

    fun `test on fn attr 2`() = testAttributePattern("""
        fn bar() {
            #![foo]
             //^
        }
    """, RsPsiPattern.onFn)

    fun `test on tuple struct attr`() = testAttributePattern("""
        #[foo]
        //^
        struct Bar(u8, u8);
    """, RsPsiPattern.onTupleStruct)

    fun `test on any item struct attr`() = testAttributePattern("""
        #[foo]
        //^
        struct Bar {
            baz: u8
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item static attr`() = testAttributePattern("""
        #[foo]
        //^
        static BAR: u8 = 1;
    """, RsPsiPattern.onAnyItem)

    fun `test on any item enum attr`() = testAttributePattern("""
        #[foo]
        //^
        enum Bar {
            Baz,
            Bat
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item fn attr`() = testAttributePattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item mod attr`() = testAttributePattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item trait attr`() = testAttributePattern("""
        #[foo]
        //^
        trait Bar {
        }
    """, RsPsiPattern.onAnyItem)

    fun `test on any item crate attr`() = testAttributePattern("""
        #![foo]
         //^
    """, RsPsiPattern.onAnyItem)

    fun `test on crate attr`() = testAttributePattern("""
        #![foo]
         //^
        struct Foo(u8, u8);
    """, RsPsiPattern.onCrate)

    fun `test on drop fn attr`() = testAttributePattern("""
        struct HasDrop;

        impl Drop for HasDrop {
            #[foo]
            //^
            fn drop(&mut self) {
                println!("Dropping!");
            }
        }
    """, RsPsiPattern.onDropFn)

    fun `test on enum attr`() = testAttributePattern("""
        #[foo]
        //^
        enum Foo {
            Bar,
            Baz
        }
    """, RsPsiPattern.onEnum)

    fun `test on enum variant attr`() = testAttributePattern("""
        enum Foo {
            #[foo]
            //^
            Bar,
            Baz
        }
    """, RsPsiPattern.onEnumVariant)

    fun `test on struct-like unit struct`() = testAttributePattern("""
        #[foo]
        //^
        struct S;
    """, RsPsiPattern.onStructLike)

    fun `test on struct-like tuple struct`() = testAttributePattern("""
        #[foo]
        //^
        struct S(u32);
    """, RsPsiPattern.onStructLike)

    fun `test on struct-like struct`() = testAttributePattern("""
        #[foo]
        //^
        struct S { a: u32 }
    """, RsPsiPattern.onStructLike)

    fun `test on struct-like enum`() = testAttributePattern("""
        #[foo]
        //^
        enum Foo {
            A
        }
    """, RsPsiPattern.onStructLike)

    fun `test on struct-like enum variant`() = testAttributePattern("""
        enum Foo {
            #[foo]
            //^
            A
        }
    """, RsPsiPattern.onStructLike)

    fun `test on extern block attr`() = testAttributePattern("""
        #[foo]
        //^
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, RsPsiPattern.onExternBlock)

    fun `test on extern block decl attr`() = testAttributePattern("""
        extern {
            #[foo]
            //^
            fn bar(baz: size_t) -> size_t;
        }
    """, RsPsiPattern.onExternBlockDecl)

    fun `test on extern crate attr`() = testAttributePattern("""
        #[foo]
        //^
        extern crate bar;
    """, RsPsiPattern.onExternCrate)

    fun `test on macro definition`() = testAttributePattern("""
        #[foo]
        //^
        macro_rules! bar {
        }
    """, RsPsiPattern.onMacro)

    fun `test on mod attr`() = testAttributePattern("""
        #[foo]
        //^
        mod bar {
        }
    """, RsPsiPattern.onMod)

    fun `test on mod attr 2`() = testAttributePattern("""
        mod bar {
            #![foo]
             //^
        }
    """, RsPsiPattern.onMod)

    fun `test on mod attr 3`() = testAttributePattern("""
        #[foo]
        //^
        mod bar;
    """, RsPsiPattern.onMod)

    fun `test on static attr`() = testAttributePattern("""
        #[foo]
        //^
        static BAR: u8 = 5;
    """, RsPsiPattern.onStatic)

    fun `test on static mut attr`() = testAttributePattern("""
        #[foo]
        //^
        static mut BAR: u8 = 5;
    """, RsPsiPattern.onStaticMut)

    fun `test on test fn attr`() = testAttributePattern("""
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

    fun `test derived trait meta item`() = testPattern("""
        #[derive(Debug)]
                 //^
        struct Foo(i32);
    """, RsPsiPattern.derivedTraitMetaItem)

    fun `test derived trait meta item in cfg_attr`() = testPattern("""
        #[cfg_attr(unix, derive(Debug))]
                                //^
        struct Foo(i32);
    """, RsPsiPattern.derivedTraitMetaItem)

    fun `test literal in include macro`() = testPattern("""
        include!("foo.rs");
                  //^
    """, RsPsiPattern.includeMacroLiteral)

    fun `test literal in path attr on mod decl`() = testPattern("""
        #[path="bar.rs"]
                //^
        mod foo;
    """, RsPsiPattern.pathAttrLiteral)

    fun `test literal in path attr on mod`() = testPattern("""
        #[path="bar.rs"]
                //^
        mod foo {}
    """, RsPsiPattern.pathAttrLiteral)

    fun `test a root meta item 1`() = testPattern("""
        #[foo]
        //^
        fn foo() {}
    """, RsPsiPattern.rootMetaItem)

    fun `test a root meta item 2`() = testPattern("""
        #![foo]
         //^
    """, RsPsiPattern.rootMetaItem)

    fun `test a root meta item 3`() = testPattern("""
        #[cfg_attr(foo, bar)]
        fn foo() {}   //^
    """, RsPsiPattern.rootMetaItem)

    fun `test a root meta item 4`() = testPattern("""
        #[cfg_attr(foo, bar, baz)]
        fn foo() {}        //^
    """, RsPsiPattern.rootMetaItem)

    fun `test a root meta item 5`() = testPattern("""
        #[cfg_attr(foo, cfg_attr(bar, baz))]
        fn foo() {}                  //^
    """, RsPsiPattern.rootMetaItem)

    fun `test not a root meta item 1`() = testPatternNegative("""
        #[cfg_attr(foo, bar)]
        fn foo() {}//^
    """, RsPsiPattern.rootMetaItem)

    fun `test not a root meta item 2`() = testPatternNegative("""
        #[cfg_attr(foo, cfg_attr(bar, baz))]
        fn foo() {}            //^
    """, RsPsiPattern.rootMetaItem)

    fun `test not a root meta item 3`() = testPatternNegative("""
        #[foo(bar())]
             //^
        fn foo() {}
    """, RsPsiPattern.rootMetaItem)

    fun `test cfg condition in cfg`() = testPattern("""
        #[cfg(foo)]
            //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test cfg condition in cfg_attr`() = testPattern("""
        #[cfg_attr(foo)]
                 //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test cfg condition in doc cfg`() = testPattern("""
        #[doc(cfg(foo))]
                //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test cfg condition in cfg in cfg_attr`() = testPattern("""
        #[cfg_attr(windows, cfg(foo))]
                              //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test not cfg condition 1`() = testPatternNegative("""
        #[cfg(foo)]
          //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test not cfg condition 2`() = testPatternNegative("""
        #[cfg_attr(foo)]
          //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test not cfg condition 3`() = testPatternNegative("""
        #[doc(cfg(foo))]
             //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test not cfg condition 4`() = testPatternNegative("""
        #[cfg_attr(foo, bar)]
                       //^
        fn foo() {}
    """, RsPsiPattern.anyCfgCondition)

    fun `test cfg feature`() = testPattern("""
        #[cfg(feature = "foo")]
        fn foo() {}   //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test inner attribute cfg feature`() = testPattern("""
        fn foo() {
            #![cfg(feature = "foo")]
        }                   //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test nested cfg feature`() = testPattern("""
        #[cfg(not(feature = "foo"))]
        fn foo() {}        //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test not a cfg feature`() = testPatternNegative("""
        #[zfg(feature = "foo")]
        fn foo() {}    //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test cfg not a feature`() = testPatternNegative("""
        #[cfg(not_a_feature = "foo")]
        fn foo() {}          //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test cfg_attr feature`() = testPattern("""
        #[cfg_attr(feature = "foo", allow(all))]
        fn foo() {}          //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test not right part of cfg_attr 1`() = testPatternNegative("""
        #[cfg_attr(windows, foo(feature = "foo"))]
        fn foo() {}                      //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test not right part of cfg_attr 2`() = testPatternNegative("""
        #[cfg_attr(windows, foo(cfg(feature = "foo")))]
        fn foo() {}                          //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test cfg at right part of cfg_attr`() = testPattern("""
        #[cfg_attr(windows, cfg(feature = "foo"))]
        fn foo() {}                      //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test nested cfg_attr feature`() = testPattern("""
        #[cfg_attr(windows, cfg_attr(feature = "foo", allow(all)))]
        fn foo() {}                           //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test nested cfg_attr cfg feature`() = testPattern("""
        #[cfg_attr(windows, cfg_attr(foobar, cfg(feature = "foo")))]
        fn foo() {}                                       //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test doc cfg`() = testPattern("""
        #[doc(cfg(feature = "foo"))]
        fn foo() {}        //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test doc cfg at right part of cfg_attr`() = testPattern("""
        #[cfg_attr(windows, doc(cfg(feature = "foo")))]
        fn foo() {}                          //^
    """, RsPsiPattern.anyCfgFeature)

    fun `test in cfg feature`() = testPattern("""
        #[cfg(feature = "foo")]
        fn foo() {}    //^
    """, RsPsiPattern.insideAnyCfgFeature)

    fun `test in cfg feature without literal`() = testPattern("""
        #[cfg(feature = foo)]
        fn foo() {}    //^
    """, RsPsiPattern.insideAnyCfgFeature)

    fun `test not in cfg feature without literal`() = testPatternNegative("""
        #[cfg( f = foo)]
        fn foo() {}//^
    """, RsPsiPattern.insideAnyCfgFeature)

    private inline fun <reified T : PsiElement> testPattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        InlineFile(code)
        val element = findElementInEditor<T>()
        assertTrue(pattern.accepts(element))
    }

    private inline fun <reified T : PsiElement> testAttributePattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        testPattern(code, pattern)
        val cfgAttrPrefix = "cfg_attr(unix, "
        val codeWithCfgAttr = code.replace("""(#!?)\[foo]""".toRegex(), "$1[${cfgAttrPrefix}foo)]")
            .replace("//^", " ".repeat(cfgAttrPrefix.length) + "//^")

        testPattern(codeWithCfgAttr, pattern)
    }

    private inline fun <reified T : PsiElement> testPatternNegative(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        InlineFile(code)
        val element = findElementInEditor<T>()
        assertFalse(pattern.accepts(element, null))
    }
}
