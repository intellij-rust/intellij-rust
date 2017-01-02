package org.rust.lang.core.completion

class RustAttributeCompletionTest : RustCompletionTestBase() {
    override val dataPath: String get() = ""

    fun testDeriveOnStruct() = checkSingleCompletion("derive", """
        #[der/*caret*/]
        struct Bar;
    """)

    fun testWarnOnTrait() = checkSingleCompletion("warn", """
        #[war/*caret*/]
        trait Bar {}
    """)

    fun testInlineOnFn() = checkSingleCompletion("inline", """
        #[inl/*caret*/]
        fn foo() {}
    """)

    fun testAllowOnFn() = checkSingleCompletion("allow", """
        #[all/*caret*/]
        fn foo() {}
    """)

    fun testSimdOnTupleStruct() = checkSingleCompletion("simd", """
        #[si/*caret*/]
        struct Bar(u8, u8);
    """)

    fun testAllowOnStatic() = checkSingleCompletion("allow", """
        #[allo/*caret*/]
        static BAR: u8 = 1;
    """)

    fun testThreadLocalOnStaticMut() = checkSingleCompletion("thread_local", """
        #[thre/*caret*/]
        static mut BAR: u8 = 1;
    """)

    fun testDenyOnEnum() = checkSingleCompletion("deny", """
        #[den/*caret*/]
        enum Foo {
            BAR,
            BAZ
        }
    """)

    fun testNoMangleOnEnum() = checkSingleCompletion("no_mangle", """
        #[no_ma/*caret*/]
        mod foo {}
    """)

    fun testOuterDenyOnFile() = checkSingleCompletion("deny", """
        #![den/*caret*/]
    """)

    fun testMacroUseOnMod() = checkSingleCompletion("macro_use", """
        #[macr/*caret*/]
        mod foo {}
    """)

    fun testOuterWarnOnMod() = checkSingleCompletion("warn", """
        mod foo {
            #![war/*caret*/]
        }
    """)

    fun testExportNameOnTraitImplMethod() = checkSingleCompletion("allow", """
        struct HasDrop;
        impl Drop for HasDrop {
            #[allo/*caret*/]
            fn drop(&mut self) {}
        }
    """)

    fun testLinkedFromOnExternBlock() = checkSingleCompletion("linked_from", """
        #[linke/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun testLinkageOnExternBlockDecl() = checkSingleCompletion("linkage", """
        extern {
            #[linka/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun testNoLinkOnExternCrate() = checkSingleCompletion("no_link", """
        #[no_l/*caret*/]
        extern crate bar;
    """)

    fun testMacroExportOnMacro() = checkSingleCompletion("macro_export", """
        #[macr/*caret*/]
        macro_rules! bar {}
    """)

    fun testCfgOnMod() = checkContainsCompletion("cfg", """
        #[cf/*caret*/]
        mod foo {}
    """)

    fun testCfgOnFileInner() = checkContainsCompletion("cfg", """
        #![cf/*caret*/]
    """)
}
