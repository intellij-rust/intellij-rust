/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.psi.impl.DebugUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.resolve2.util.buildStub

class RsBlockStubTest : RsTestBase() {

    fun `test empty`() = doTest("""
        fn f() {}
    """, """
        BLOCK:RsPlaceholderStub
    """)

    fun `test not stubbed`() = doTest("""
        fn f() {
            0;
            let x = 1;
            func();
            impl Foo {}
            // block expr
            { 1 }
        }
    """, """
        BLOCK:RsPlaceholderStub
    """)

    fun `test imports`() = doTest("""
        fn f() {
            use foo1::item1;
            use foo2::item2 as item3;
            use foo3::{item4, item5};
        }
    """, """
        BLOCK:RsPlaceholderStub
          USE_ITEM:RsUseItemStub
            USE_SPECK:RsUseSpeckStub
              PATH:RsPathStub
                PATH:RsPathStub
          USE_ITEM:RsUseItemStub
            USE_SPECK:RsUseSpeckStub
              PATH:RsPathStub
                PATH:RsPathStub
              ALIAS:RsAliasStub
          USE_ITEM:RsUseItemStub
            USE_SPECK:RsUseSpeckStub
              PATH:RsPathStub
              USE_GROUP:RsPlaceholderStub
                USE_SPECK:RsUseSpeckStub
                  PATH:RsPathStub
                USE_SPECK:RsUseSpeckStub
                  PATH:RsPathStub
    """)

    fun `test items`() = doTest("""
        fn f() {
            fn func(a: i32) -> i32 { 0 }
            struct Struct1;
            struct Struct2(i32);
            struct Struct3 { a: i32 }
            enum E {
                E1,
                E2(i32),
                E3 { a: i32 },
            }
        }
    """, """
        BLOCK:RsPlaceholderStub
          FUNCTION:RsFunctionStub
          STRUCT_ITEM:RsStructItemStub
          STRUCT_ITEM:RsStructItemStub
          STRUCT_ITEM:RsStructItemStub
          ENUM_ITEM:RsEnumItemStub
            ENUM_BODY:RsPlaceholderStub
              ENUM_VARIANT:RsEnumVariantStub
              ENUM_VARIANT:RsEnumVariantStub
              ENUM_VARIANT:RsEnumVariantStub
    """)

    fun `test macros`() = doTest("""
        fn f() {
            macro macro1() { 1 }
            macro_rules! macro2 { () => { 1 }; }
            macro1!(1);
        }
    """, """
        BLOCK:RsPlaceholderStub
          MACRO_2:RsMacro2Stub
          MACRO:RsMacroStub
          MACRO_CALL:RsMacroCallStub
            PATH:RsPathStub
    """)

    private fun doTest(@Language("Rust") code: String, expectedStubText: String) {
        InlineFile(code)
        val file = myFixture.file
        val block = file.childOfType<RsFunction>()!!.block!!
        val stub = block.buildStub()!!
        val stubText = DebugUtil.stubTreeToString(stub)
        assertEquals(expectedStubText.trimIndent() + "\n", stubText)
    }
}
