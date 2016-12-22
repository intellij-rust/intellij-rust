package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language

class RustAttributeCompletionPatternTest : RustCompletionTestBase() {
    override val dataPath: String get() = ""

    fun testOnStruct() = testPattern("""
        #[foo]
        //^
        struct Bar;
    """, AttributeCompletionProvider.onStruct)

    fun testOnFn() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, AttributeCompletionProvider.onFn)

    fun testOnTupleStruct() = testPattern("""
        #[foo]
        //^
        struct Bar(u8, u8);
    """, AttributeCompletionProvider.onTupleStruct)

    fun testOnAnyItemStruct() = testPattern("""
        #[foo]
        //^
        struct Bar {
            baz: u8
        }
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnAnyItemStatic() = testPattern("""
        #[foo]
        //^
        static bar: u8 = 1;
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnAnyItemEnum() = testPattern("""
        #[foo]
        //^
        enum Bar {
            Baz,
            Bat
        }
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnAnyItemFn() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnAnyItemMod() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnAnyItemTrait() = testPattern("""
        #[foo]
        //^
        trait Bar {
        }
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnAnyItemCrate() = testPattern("""
        #![foo]
         //^
    """, AttributeCompletionProvider.onAnyItem)

    fun testOnCrate() = testPattern("""
        #![foo]
         //^
        struct Foo(u8, u8);
    """, AttributeCompletionProvider.onCrate)

    fun testOnDropFn() = testPattern("""
        struct HasDrop;

        impl Drop for HasDrop {
            #[foo]
            //^
            fn drop(&mut self) {
                println!("Dropping!");
            }
        }
    """, AttributeCompletionProvider.onDropFn)

    fun testOnEnum() = testPattern("""
        #[foo]
        //^
        enum Foo {
            Bar,
            Baz
        }
    """, AttributeCompletionProvider.onEnum)

    fun testOnExternBlock() = testPattern("""
        #[foo]
        //^
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, AttributeCompletionProvider.onExternBlock)

    fun testOnExternBlockDecl() = testPattern("""
        extern {
            #[foo]
            //^
            fn bar(baz: size_t) -> size_t;
        }
    """, AttributeCompletionProvider.onExternBlockDecl)

    fun testOnExternCrate() = testPattern("""
        #[foo]
        //^
        extern crate bar;
    """, AttributeCompletionProvider.onExternCrate)

    fun testOnMacro() = testPattern("""
        #[foo]
        //^
        macro_rules! bar {
        }
    """, AttributeCompletionProvider.onMacro)

    fun testOnMod() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, AttributeCompletionProvider.onMod)

    fun testOnStatic() = testPattern("""
        #[foo]
        //^
        static bar: u8 = 5;
    """, AttributeCompletionProvider.onStatic)

    fun testOnStaticMut() = testPattern("""
        #[foo]
        //^
        static mut bar: u8 = 5;
    """, AttributeCompletionProvider.onStaticMut)

    fun testOnTestFn() = testPattern("""
        #[test]
        #[foo]
        //^
        fn testBar() {

        }
    """, AttributeCompletionProvider.onTestFn)

    private fun <T> testPattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        InlineFile(code)
        val element = findElementInEditor<PsiElement>()
        assertTrue(pattern.accepts(element))
    }
}
