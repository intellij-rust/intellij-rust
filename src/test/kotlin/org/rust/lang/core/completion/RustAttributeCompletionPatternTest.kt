package org.rust.lang.core.completion

import org.rust.ide.completion.AttributeCompletionProvider
import org.rust.lang.core.pattern.RustPatternTestBase

class RustAttributeCompletionPatternTest : RustPatternTestBase() {
    //language=Rust
    fun testOnStruct() = testPattern("""
        #[foo]
        //^
        struct Bar;
    """, AttributeCompletionProvider.onStruct)

    //language=Rust
    fun testOnFn() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, AttributeCompletionProvider.onFn)

    //language=Rust
    fun testOnTupleStruct() = testPattern("""
        #[foo]
        //^
        struct Bar(u8, u8);
    """, AttributeCompletionProvider.onTupleStruct)

    //language=Rust
    fun testOnAnyItemStruct() = testPattern("""
        #[foo]
        //^
        struct Bar {
            baz: u8
        }
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnAnyItemStatic() = testPattern("""
        #[foo]
        //^
        static bar: u8 = 1;
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnAnyItemEnum() = testPattern("""
        #[foo]
        //^
        enum Bar {
            Baz,
            Bat
        }
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnAnyItemFn() = testPattern("""
        #[foo]
        //^
        fn bar() {
        }
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnAnyItemMod() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnAnyItemTrait() = testPattern("""
        #[foo]
        //^
        trait Bar {
        }
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnAnyItemCrate() = testPattern("""
        #![foo]
         //^
    """, AttributeCompletionProvider.onAnyItem)

    //language=Rust
    fun testOnCrate() = testPattern("""
        #![foo]
         //^
        struct Foo(u8, u8);
    """, AttributeCompletionProvider.onCrate)

    //language=Rust
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

    //language=Rust
    fun testOnEnum() = testPattern("""
        #[foo]
        //^
        enum Foo {
            Bar,
            Baz
        }
    """, AttributeCompletionProvider.onEnum)

    //language=Rust
    fun testOnExternBlock() = testPattern("""
        #[foo]
        //^
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, AttributeCompletionProvider.onExternBlock)

    //language=Rust
    fun testOnExternBlockDecl() = testPattern("""
        extern {
            #[foo]
            //^
            fn bar(baz: size_t) -> size_t;
        }
    """, AttributeCompletionProvider.onExternBlockDecl)

    //language=Rust
    fun testOnExternCrate() = testPattern("""
        #[foo]
        //^
        extern crate bar;
    """, AttributeCompletionProvider.onExternCrate)

    //language=Rust
    fun testOnMacro() = testPattern("""
        #[foo]
        //^
        macro_rules! bar {
        }
    """, AttributeCompletionProvider.onMacro)

    //language=Rust
    fun testOnMod() = testPattern("""
        #[foo]
        //^
        mod bar {
        }
    """, AttributeCompletionProvider.onMod)

    //language=Rust
    fun testOnStatic() = testPattern("""
        #[foo]
        //^
        static bar: u8 = 5;
    """, AttributeCompletionProvider.onStatic)

    //language=Rust
    fun testOnStaticMut() = testPattern("""
        #[foo]
        //^
        static mut bar: u8 = 5;
    """, AttributeCompletionProvider.onStaticMut)

    //language=Rust
    fun testOnTestFn() = testPattern("""
        #[test]
        #[foo]
        //^
        fn testBar() {

        }
    """, AttributeCompletionProvider.onTestFn)
}
