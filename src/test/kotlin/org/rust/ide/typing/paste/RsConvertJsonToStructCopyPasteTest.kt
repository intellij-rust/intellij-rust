/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.paste

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import java.awt.datatransfer.StringSelection

class RsConvertJsonToStructCopyPasteTest : RsTestBase() {
    fun `test paste into string literal`() = doCopyPasteTest("""
        //- lib.rs
        fn foo() {
            let a = "/*caret*/";
        }
    """, """
        //- lib.rs
        fn foo() {
            let a = "{"a": null, "b": []}";
        }
    """, """{"a": null, "b": []}""")

    fun `test paste into function`() = doCopyPasteTest("""
        //- lib.rs
        fn foo() {
            /*caret*/
        }
    """, """
        //- lib.rs
        fn foo() {
            {"a": null, "b": []}
        }
    """, """{"a": null, "b": []}""")

    fun `test paste into empty document`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: i64,
        }
    """, """{"a":  5}""")

    fun `test paste into module`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            /*caret*/

            struct S;
        }
    """, """
        //- lib.rs
        mod foo {
            struct Struct {
                pub a: i64,
            }

            struct S;
        }
    """, """{"a":  5}""")

    fun `test paste reserved identifier`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub r#type: i64,
        }
    """, """{"type":  5}""")

    fun `test paste invalid identifier`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub _oi_d: i64,
        }
    """, """{"${'$'}oi/d":  5}""")

    fun `test paste numeric identifier`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub _1: i64,
        }
    """, """{"1":  5}""")

    fun `test paste array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        [1]
    """, "[1]")

    fun `test paste number`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        1
    """, "1")

    fun `test invalid json`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        {a:
    """, """{a:""")

    fun `test empty struct`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {}
    """, "{}")

    fun `test duplicated struct keys`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: bool,
        }
    """, """{"a": true, "a": 0}""")

    fun `test bool true field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: bool,
        }
    """, """{"a": true}""")

    fun `test bool false field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: bool,
        }
    """, """{"a": false}""")

    fun `test integer field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: i64,
        }
    """, """{"a": 0}""")

    fun `test float field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: f64,
        }
    """, """{"a": 0.1}""")

    fun `test string field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: String,
        }
    """, """{"a": "foo"}""")

    fun `test null field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Option<_>,
        }
    """, """{"a": null}""")

    fun `test empty array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<_>,
        }
    """, """{"a": []}""")

    fun `test integer array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<i64>,
        }
    """, """{"a": [1, 2, 3]}""")

    fun `test null array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<Option<_>>,
        }
    """, """{"a": [null]}""")

    fun `test nullable type array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<Option<i64>>,
        }
    """, """{"a": [1, null]}""")

    fun `test nested array in array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<Vec<i64>>,
        }
    """, """{"a": [[1], [2]]}""")

    fun `test nested struct in array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub b: i64,
        }

        struct Struct2 {
            pub a: Vec<Struct1>,
        }
    """, """{"a": [{"b": 5}, {"b": 4}]}""")

    fun `test mixed array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<_>,
        }
    """, """{"a": [1, true]}""")

    fun `test mixed array ignore encountered struct`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<_>,
        }
    """, """{"a": [1, true, {"c":  5}]}""")

    fun `test multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: String,
            pub b: i64,
            pub bar: bool,
        }
    """, """{"a": "foo", "b": 1, "bar": true}""")

    fun `test object field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub foo: i64,
        }

        struct Struct2 {
            pub a: Struct1,
        }
    """, """{"a": {"foo": 5}}""")

    fun `test same object appears multiple times`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub foo: i64,
        }

        struct Struct2 {
            pub a: Struct1,
            pub b: Struct1,
        }
    """, """{"a": {"foo": 5}, "b":  {"foo": 3}}""")

    fun `test same object appears multiple times with different field order`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub bar: bool,
            pub foo: i64,
        }

        struct Struct2 {
            pub a: Struct1,
            pub b: Struct1,
        }
    """, """{"a": {"bar": false, "foo": 5}, "b":  {"foo": 3, "bar": true}}""")

    fun `test fill struct name`() = doCopyPasteTestWithLiveTemplate("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Foo {
            pub a: i64,
        }
    """, """{"a": 0}""", "Foo\t")

    fun `test rename struct references`() = doCopyPasteTestWithLiveTemplate("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Foo {
            pub b: i64,
        }

        struct Bar {
            pub a: Foo,
            pub b: Foo,
        }
    """, """{"a": {"b": 5}, "b": {"b": 5}}""", "Foo\tBar\t")

    fun `test struct field to snake case`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub my_long_field: i64,
        }
    """, """{"MyLongField": 1}""")

    fun `test fill underscore types`() = doCopyPasteTestWithLiveTemplate("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Foo {
            pub a: Option<u32>,
            pub b: Vec<bool>,
        }
    """, """{"a": null, "b": []}""", "Foo\tu32\tbool\t")

    fun `test allow trailing commas`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<i64>,
        }
    """, """{"a": [1, 2, 3,],}""")

    override fun setUp() {
        super.setUp()
        CONVERT_JSON_ON_PASTE.setValue(true, testRootDisposable)
    }

    private fun doCopyPasteTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String
    ) = doTest(before, after, toPaste)

    private fun doCopyPasteTestWithLiveTemplate(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String,
        toType: String
    ) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)

        doTest(before, after, toPaste) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

            assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
            myFixture.type(toType)
            assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        }
    }

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String,
        action: () -> Unit = {}
    ) {
        val testProject = fileTreeFromText(before).create()
        CopyPasteManager.getInstance().setContents(StringSelection(toPaste))

        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        action()

        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }
}
