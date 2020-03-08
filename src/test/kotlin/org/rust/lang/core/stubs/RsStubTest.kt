/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.stubs.StubTreeLoader
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText

class RsStubTest : RsTestBase() {

    fun `test literal is not stubbed inside statement`() = doTest("""
        fn foo() { 0; }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
    """)

    fun `test expression is not stubbed inside statement`() = doTest("""
        fn foo() { 2 + 2; }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
    """)

    fun `test literal is not stubbed inside function tail expr`() = doTest("""
        fn foo() -> i32 { 0 }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            RET_TYPE:RsPlaceholderStub
              TYPE_REFERENCE:RsPlaceholderStub
                BASE_TYPE:RsBaseTypeStub
                  PATH:RsPathStub
    """)

    fun `test expression is not stubbed inside function tail expr`() = doTest("""
        fn foo() -> i32 { 2 + 2 }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            RET_TYPE:RsPlaceholderStub
              TYPE_REFERENCE:RsPlaceholderStub
                BASE_TYPE:RsBaseTypeStub
                  PATH:RsPathStub
    """)

    fun `test lifetime is stubbed inside function signature`() = doTest("""
        fn foo<'a>(x: &'a str) -> i32 { 32 }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            TYPE_PARAMETER_LIST:RsPlaceholderStub
              LIFETIME_PARAMETER:RsLifetimeParameterStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
              VALUE_PARAMETER:RsValueParameterStub
                TYPE_REFERENCE:RsPlaceholderStub
                  REF_LIKE_TYPE:RsRefLikeTypeStub
                    LIFETIME:RsLifetimeStub
                    TYPE_REFERENCE:RsPlaceholderStub
                      BASE_TYPE:RsBaseTypeStub
                        PATH:RsPathStub
            RET_TYPE:RsPlaceholderStub
              TYPE_REFERENCE:RsPlaceholderStub
                BASE_TYPE:RsBaseTypeStub
                  PATH:RsPathStub
    """)

    fun `test literal is not stubbed inside closure tail expr`() = doTest("""
        fn foo() {
            || -> i32 { 0 };
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
    """)

    fun `test expression is not stubbed inside closure tail expr`() = doTest("""
        fn foo() {
            || -> i32 { 2 + 2 };
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
    """)

    fun `test literal is stubbed inside const body`() = doTest("""
        const C: i32 = 0;
    """, """
        RsFileStub
          CONSTANT:RsConstantStub
            TYPE_REFERENCE:RsPlaceholderStub
              BASE_TYPE:RsBaseTypeStub
                PATH:RsPathStub
            LIT_EXPR:RsLitExprStub
    """)

    fun `test expression is stubbed inside const body`() = doTest("""
        const C: i32 = 2 + 2;
    """, """
        RsFileStub
          CONSTANT:RsConstantStub
            TYPE_REFERENCE:RsPlaceholderStub
              BASE_TYPE:RsBaseTypeStub
                PATH:RsPathStub
            BINARY_EXPR:RsPlaceholderStub
              LIT_EXPR:RsLitExprStub
              BINARY_OP:RsBinaryOpStub
              LIT_EXPR:RsLitExprStub
    """)

    fun `test literal is stubbed inside array type`() = doTest("""
        type T = [u8; 1];
    """, """
        RsFileStub
          TYPE_ALIAS:RsTypeAliasStub
            TYPE_REFERENCE:RsPlaceholderStub
              ARRAY_TYPE:RsArrayTypeStub
                TYPE_REFERENCE:RsPlaceholderStub
                  BASE_TYPE:RsBaseTypeStub
                    PATH:RsPathStub
                LIT_EXPR:RsLitExprStub
    """)

    fun `test expression is stubbed inside array type`() = doTest("""
        type T = [u8; 2 + 2];
    """, """
        RsFileStub
          TYPE_ALIAS:RsTypeAliasStub
            TYPE_REFERENCE:RsPlaceholderStub
              ARRAY_TYPE:RsArrayTypeStub
                TYPE_REFERENCE:RsPlaceholderStub
                  BASE_TYPE:RsBaseTypeStub
                    PATH:RsPathStub
                BINARY_EXPR:RsPlaceholderStub
                  LIT_EXPR:RsLitExprStub
                  BINARY_OP:RsBinaryOpStub
                  LIT_EXPR:RsLitExprStub
    """)

    fun `test function block is stubbed if contains item`() = doTest("""
        fn foo() {
            struct S;
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              STRUCT_ITEM:RsStructItemStub
    """)

    fun `test function block is stubbed if contains union`() = doTest("""
        fn foo() {
            union Foo {}
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              STRUCT_ITEM:RsStructItemStub
                BLOCK_FIELDS:RsPlaceholderStub
    """)

    fun `test function block is stubbed if contains inner attrs`() = doTest("""
        fn foo() {
            #![foo]
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              INNER_ATTR:RsInnerAttrStub
                META_ITEM:RsMetaItemStub
                  PATH:RsPathStub
    """)

    fun `test nested block is stubbed if contains items`() = doTest("""
        fn foo() {
            if true {
                struct S;
            } else {
                foobar();
            }
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              BLOCK:RsPlaceholderStub
                STRUCT_ITEM:RsStructItemStub
    """)

    fun `test literal is not stubbed inside nested block tail expr`() = doTest("""
        fn foo() {
            if true {
                struct S;
                0
            } else {
                0
            };
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              BLOCK:RsPlaceholderStub
                STRUCT_ITEM:RsStructItemStub
    """)

    fun `test expression is not stubbed inside nested block tail expr`() = doTest("""
        fn foo() {
            if true {
                struct S;
                1 + 2
            } else if false {
                3 + 4
            } else {
                struct T;
                -5
            };
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              BLOCK:RsPlaceholderStub
                STRUCT_ITEM:RsStructItemStub
              BLOCK:RsPlaceholderStub
                STRUCT_ITEM:RsStructItemStub
    """)

    fun `test expressions are stubbed inside function local module`() = doTest("""
        fn foo() {
            mod bar {
                include!("a.rs");
            }
        }
    """, """
        RsFileStub
          FUNCTION:RsFunctionStub
            VALUE_PARAMETER_LIST:RsPlaceholderStub
            BLOCK:RsPlaceholderStub
              MOD_ITEM:RsModItemStub
                MACRO_CALL:RsMacroCallStub
                  PATH:RsPathStub
                  INCLUDE_MACRO_ARGUMENT:RsPlaceholderStub
                    LIT_EXPR:RsLitExprStub
    """)

    fun `test expressions are stubs inside file`() = doTest("""
        include!("foo.rs");
    """, """
        RsFileStub
          MACRO_CALL:RsMacroCallStub
            PATH:RsPathStub
            INCLUDE_MACRO_ARGUMENT:RsPlaceholderStub
              LIT_EXPR:RsLitExprStub
    """)

    private fun doTest(@Language("Rust") code: String, expectedStubText: String) {
        val fileName = "main.rs"
        fileTreeFromText("//- $fileName\n$code").create()
        val vFile = myFixture.findFileInTempDir(fileName)
        val stubTree = StubTreeLoader.getInstance().readFromVFile(project, vFile) ?: error("Stub tree is null")
        val stubText = DebugUtil.stubTreeToString(stubTree.root)
        assertEquals(expectedStubText.trimIndent() + "\n", stubText)
    }
}
