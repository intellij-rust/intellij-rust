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

    fun `test nullable type array 1`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<Option<i64>>,
        }
    """, """{"a": [1, null]}""")

    fun `test nullable type array 2`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<Option<i64>>,
        }
    """, """{"a": [null, 1]}""")

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

    fun `test struct lowercase single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            pub field: i64,
        }
    """, """{"field": 1}""", hasSerde = true)

    fun `test struct lowercase multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            pub field: i64,
            pub bar: i64,
        }
    """, """{"field": 1, "bar": 1}""", hasSerde = true)

    fun `test struct snake case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            pub my_long_field: i64,
        }
    """, """{"my_long_field": 1}""", hasSerde = true)

    fun `test struct snake case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            pub my_long_field: i64,
            pub my_long_field_b: i64,
        }
    """, """{"my_long_field": 1, "my_long_field_b": 1}""", hasSerde = true)

    fun `test struct kebab case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "my-long-field")]
            pub my_long_field: i64,
        }
    """, """{"my-long-field": 1}""", hasSerde = true)

    fun `test struct kebab case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "my-long-field")]
            pub my_long_field: i64,
            #[serde(rename = "my-long-field-2")]
            pub my_long_field_2: i64,
        }
    """, """{"my-long-field": 1, "my-long-field-2": 2}""", hasSerde = true)

    fun `test struct camel case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "myLongField")]
            pub my_long_field: i64,
        }
    """, """{"myLongField": 1}""", hasSerde = true)

    fun `test struct camel case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "myLongField")]
            pub my_long_field: i64,
            #[serde(rename = "myLongFieldB")]
            pub my_long_field_b: i64,
        }
    """, """{"myLongField": 1, "myLongFieldB": 2}""", hasSerde = true)

    fun `test struct pascal case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "MyLongField")]
            pub my_long_field: i64,
        }
    """, """{"MyLongField": 1}""", hasSerde = true)

    fun `test struct pascal case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "MyLongField")]
            pub my_long_field: i64,
            #[serde(rename = "MyLongFieldB")]
            pub my_long_field_b: i64,
        }
    """, """{"MyLongField": 1, "MyLongFieldB": 1}""", hasSerde = true)

    fun `test struct upper case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "FIELD")]
            pub field: i64,
        }
    """, """{"FIELD": 1}""", hasSerde = true)

    fun `test struct upper case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "FIELD")]
            pub field: i64,
            #[serde(rename = "BAR")]
            pub bar: i64,
        }
    """, """{"FIELD": 1, "BAR": 1}""", hasSerde = true)

    fun `test struct screaming snake case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "FOO_BAR")]
            pub foo_bar: i64,
        }
    """, """{"FOO_BAR": 1}""", hasSerde = true)

    fun `test struct screaming snake case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "FOO_BAR")]
            pub foo_bar: i64,
            #[serde(rename = "BAR_BAZ")]
            pub bar_baz: i64,
        }
    """, """{"FOO_BAR": 1, "BAR_BAZ": 1}""", hasSerde = true)

    fun `test struct screaming kebab case single field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "MY-LONG-FIELD")]
            pub my_long_field: i64,
        }
    """, """{"MY-LONG-FIELD": 1}""", hasSerde = true)

    fun `test struct screaming kebab case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "MY-LONG-FIELD")]
            pub my_long_field: i64,
            #[serde(rename = "ANOTHER-FIELD")]
            pub another_field: i64,
        }
    """, """{"MY-LONG-FIELD": 1, "ANOTHER-FIELD": 1}""", hasSerde = true)

    fun `test struct field with multiple conventions`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "foo_bar-baz")]
            pub foo_bar_baz: i64,
            #[serde(rename = "bar_baz-foo")]
            pub bar_baz_foo: i64,
        }
    """, """{"foo_bar-baz": 1, "bar_baz-foo": 1}""", hasSerde = true)

    fun `test struct rename invalid fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            #[serde(rename = "f@")]
            pub f: i64,
            #[serde(rename = "0")]
            pub _0: i64,
            #[serde(rename = "#")]
            pub _field: i64,
            #[serde(rename = "\"asd\"")]
            pub _asd: i64,
        }
    """, """{"f@": 1, "0": 1, "#": 1, "\"asd\"": 1}""", hasSerde = true)

    fun `test struct duplicated field names`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Struct {
            pub _0: i64,
            #[serde(rename = "0")]
            pub _0_0: i64,
        }
    """, """{"_0": 1, "0": 1}""", hasSerde = true)

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

    fun `test unify structs in array 1`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub a: Option<i64>,
            pub b: Option<i64>,
        }

        struct Struct2 {
            pub items: Vec<Struct1>,
        }
    """, """{"items": [{"a": 1}, {"b":  2}]}""")

    fun `test unify structs in array 2`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub a: i64,
            pub opt_field: Option<_>,
            pub opt_field_2: Option<i64>,
        }

        struct Struct2 {
            pub items: Vec<Struct1>,
        }
    """, """{"items": [{"a": 1, "opt_field": true}, {"a":  2}, {"a":  2, "opt_field_2": 2}, {"a":  2, "opt_field": 1}]}""")

    fun `test unify structs in array 3`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub field1: i64,
            pub optional: Option<i64>,
        }

        struct Struct2 {
            pub a: Struct1,
        }

        struct Struct3 {
            pub items: Vec<Struct2>,
        }
    """, """{
  "items": [
    {
      "a": {
        "field1": 1
      }
    },
    {
      "a": {
        "field1": 1,
        "optional": 2
      }
    }
  ]
}""")

    fun `test unify structs in array 4`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct1 {
            pub field1: Option<i64>,
            pub field2: Option<i64>,
        }

        struct Struct2 {
            pub field1: Option<i64>,
            pub optional: Option<i64>,
        }

        struct Struct3 {
            pub a: Struct2,
            pub b: Option<i64>,
            pub c: Option<Struct1>,
        }

        struct Struct4 {
            pub items: Vec<Struct3>,
        }
    """, """{
  "items": [
    {
      "a": {
        "field1": 1
      },
      "b": 5,
      "c": {
        "field1": 1
      }
    },
    {
      "a": {
        "field1": 1,
        "optional": 2
      },
      "c": {
        "field2": 1
      }
    },
    {
      "a": {
        "optional": 2
      }
    }
  ]
}""")

    override fun setUp() {
        super.setUp()
        CONVERT_JSON_ON_PASTE.setValue(true, testRootDisposable)
    }

    private fun doCopyPasteTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String,
        hasSerde: Boolean = false
    ) = doTest(before, after, toPaste, hasSerde = hasSerde)

    private fun doCopyPasteTestWithLiveTemplate(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String,
        toType: String,
        hasSerde: Boolean = false
    ) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)

        doTest(before, after, toPaste, {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

            assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
            myFixture.type(toType)
            assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        }, hasSerde)
    }

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String,
        action: () -> Unit = {},
        hasSerde: Boolean
    ) {
        val testProject = fileTreeFromText(before).create()
        CopyPasteManager.getInstance().setContents(StringSelection(toPaste))

        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        convertJsonWithSerdePresent(hasSerde) {
            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
            action()
        }

        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }
}
