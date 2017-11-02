/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsAttributeCompletionTest : RsCompletionTestBase() {
    fun testDeriveOnStruct() = @Suppress("DEPRECATION") checkSingleCompletion("derive", """
        #[der/*caret*/]
        struct Bar;
    """)

    fun testWarnOnTrait() = @Suppress("DEPRECATION") checkSingleCompletion("warn", """
        #[war/*caret*/]
        trait Bar {}
    """)

    fun testInlineOnFn() = @Suppress("DEPRECATION") checkSingleCompletion("inline", """
        #[inl/*caret*/]
        fn foo() {}
    """)

    fun testAllowOnFn() = @Suppress("DEPRECATION") checkSingleCompletion("allow", """
        #[all/*caret*/]
        fn foo() {}
    """)

    fun testSimdOnTupleStruct() = @Suppress("DEPRECATION") checkSingleCompletion("simd", """
        #[si/*caret*/]
        struct Bar(u8, u8);
    """)

    fun testAllowOnStatic() = @Suppress("DEPRECATION") checkSingleCompletion("allow", """
        #[allo/*caret*/]
        static BAR: u8 = 1;
    """)

    fun testThreadLocalOnStaticMut() = @Suppress("DEPRECATION") checkSingleCompletion("thread_local", """
        #[thre/*caret*/]
        static mut BAR: u8 = 1;
    """)

    fun testDenyOnEnum() = @Suppress("DEPRECATION") checkSingleCompletion("deny", """
        #[den/*caret*/]
        enum Foo {
            BAR,
            BAZ
        }
    """)

    fun testNoMangleOnEnum() = @Suppress("DEPRECATION") checkSingleCompletion("no_mangle", """
        #[no_ma/*caret*/]
        mod foo {}
    """)

    fun testOuterDenyOnFile() = @Suppress("DEPRECATION") checkSingleCompletion("deny", """
        #![den/*caret*/]
    """)

    fun testMacroUseOnMod() = @Suppress("DEPRECATION") checkSingleCompletion("macro_use", """
        #[macr/*caret*/]
        mod foo {}
    """)

    fun testOuterWarnOnMod() = @Suppress("DEPRECATION") checkSingleCompletion("warn", """
        mod foo {
            #![war/*caret*/]
        }
    """)

    fun testExportNameOnTraitImplMethod() = @Suppress("DEPRECATION") checkSingleCompletion("allow", """
        struct HasDrop;
        impl Drop for HasDrop {
            #[allo/*caret*/]
            fn drop(&mut self) {}
        }
    """)

    fun testLinkedFromOnExternBlock() = @Suppress("DEPRECATION") checkSingleCompletion("linked_from", """
        #[linke/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun testLinkageOnExternBlockDecl() = @Suppress("DEPRECATION") checkSingleCompletion("linkage", """
        extern {
            #[linka/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun testNoLinkOnExternCrate() = @Suppress("DEPRECATION") checkSingleCompletion("no_link", """
        #[no_l/*caret*/]
        extern crate bar;
    """)

    fun testMacroExportOnMacro() = @Suppress("DEPRECATION") checkSingleCompletion("macro_export", """
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
