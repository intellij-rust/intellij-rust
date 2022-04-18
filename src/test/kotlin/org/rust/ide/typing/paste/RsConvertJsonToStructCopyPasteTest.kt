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
        struct Root {
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
            struct Root {
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
        struct Root {
            pub r#type: i64,
        }
    """, """{"type":  5}""")

    fun `test paste invalid identifier`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub _oi_d: i64,
        }
    """, """{"${'$'}oi/d":  5}""")

    fun `test paste numeric identifier`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
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
        struct Root {}
    """, "{}")

    fun `test duplicated struct keys`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: bool,
        }
    """, """{"a": true, "a": 0}""")

    fun `test bool true field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: bool,
        }
    """, """{"a": true}""")

    fun `test bool false field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: bool,
        }
    """, """{"a": false}""")

    fun `test integer field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: i64,
        }
    """, """{"a": 0}""")

    fun `test float field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: f64,
        }
    """, """{"a": 0.1}""")

    fun `test string field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: String,
        }
    """, """{"a": "foo"}""")

    fun `test null field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Option<_>,
        }
    """, """{"a": null}""")

    fun `test empty array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<_>,
        }
    """, """{"a": []}""")

    fun `test integer array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<i64>,
        }
    """, """{"a": [1, 2, 3]}""")

    fun `test null array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<Option<_>>,
        }
    """, """{"a": [null]}""")

    fun `test nullable type array 1`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<Option<i64>>,
        }
    """, """{"a": [1, null]}""")

    fun `test nullable type array 2`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<Option<i64>>,
        }
    """, """{"a": [null, 1]}""")

    fun `test nested array in array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<Vec<i64>>,
        }
    """, """{"a": [[1], [2]]}""")

    fun `test nested struct in array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub b: i64,
        }

        struct Root {
            pub a: Vec<Struct>,
        }
    """, """{"a": [{"b": 5}, {"b": 4}]}""")

    fun `test mixed array`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<_>,
        }
    """, """{"a": [1, true]}""")

    fun `test mixed array ignore encountered struct`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub a: Vec<_>,
        }
    """, """{"a": [1, true, {"c":  5}]}""")

    fun `test multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
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
        struct A {
            pub foo: i64,
        }

        struct Root {
            pub a: A,
        }
    """, """{"a": {"foo": 5}}""")

    fun `test same object appears multiple times`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub foo: i64,
        }

        struct Root {
            pub a: Struct,
            pub b: Struct,
        }
    """, """{"a": {"foo": 5}, "b":  {"foo": 3}}""")

    fun `test same object appears multiple times with different field order`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub bar: bool,
            pub foo: i64,
        }

        struct Root {
            pub a: Struct,
            pub b: Struct,
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
        struct Root {
            pub field: i64,
        }
    """, """{"field": 1}""", hasSerde = true)

    fun `test struct lowercase multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Root {
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
        struct Root {
            pub my_long_field: i64,
        }
    """, """{"my_long_field": 1}""", hasSerde = true)

    fun `test struct snake case multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        #[derive(Serialize, Deserialize)]
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
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
        struct Root {
            pub a: Vec<i64>,
        }
    """, """{"a": [1, 2, 3,],}""")

    fun `test unify structs in array disjoint types`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Option<i64>,
            pub b: Option<i64>,
        }

        struct Root {
            pub items: Vec<Struct>,
        }
    """, """{"items": [{"a": 1}, {"b":  2}]}""")

    fun `test unify structs in array one array is empty`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<String>,
        }

        struct Root {
            pub items: Vec<Struct>,
        }
    """, """{"items": [{"a": []}, {"a": ["a", "b"]}]}""")

    fun `test unify structs in array one array contains null`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Vec<Option<String>>,
        }

        struct Root {
            pub items: Vec<Struct>,
        }
    """, """{"items": [{"a": ["a", null]}, {"a": ["a", "b"]}]}""")

    fun `test unify structs in array field with multiple types`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: i64,
            pub opt_field: Option<_>,
            pub opt_field_2: Option<i64>,
        }

        struct Root {
            pub items: Vec<Struct>,
        }
    """, """{"items": [{"a": 1, "opt_field": true}, {"a":  2}, {"a":  2, "opt_field_2": 2}, {"a":  2, "opt_field": 1}]}""")

    fun `test unify structs in array nested optional field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct A {
            pub field1: i64,
            pub optional: Option<i64>,
        }

        struct Struct {
            pub a: A,
        }

        struct Root {
            pub items: Vec<Struct>,
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

    fun `test unify structs in array nested disjoint and optional keys`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct C {
            pub field1: Option<i64>,
            pub field2: Option<i64>,
        }

        struct A {
            pub field1: Option<i64>,
            pub optional: Option<i64>,
        }

        struct Struct {
            pub a: A,
            pub b: Option<i64>,
            pub c: Option<C>,
        }

        struct Root {
            pub items: Vec<Struct>,
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

    fun `test unify structs in array containing null`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: String,
            pub b: Option<i64>,
        }

        struct Root {
            pub items: Vec<Option<Struct>>,
        }
    """, """{
      "items": [
        {
          "a": "1"
        },
        {
          "a": "2",
          "b": 1
        },
        null,
        null
      ]
    }""")

    fun `test unify structs in array containing non-struct type`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Root {
            pub items: Vec<_>,
        }
    """, """{
      "items": [
        {
          "a": "1"
        },
        {
          "a": "2",
          "b": 1
        },
        1
      ]
    }""")

    fun `test unify structs in array null field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub a: Option<String>,
        }

        struct Root {
            pub items: Vec<Struct>,
        }
    """, """{
      "items": [
        {
          "a": "1"
        },
        {
          "a": null
        }
      ]
    }""")

    fun `test suggest name for inner struct`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Foo {
            pub a: i64,
        }

        struct Root {
            pub foo: Foo,
        }
    """, """{"foo": {"a":  5}}""")

    fun `test suggest name for inner struct use camel case`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct FooBar {
            pub a: i64,
        }

        struct Root {
            pub foo_bar: FooBar,
        }
    """, """{"foo_bar": {"a":  5}}""")

    fun `test suggest name conflicting names`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Foo1 {
            pub y: i64,
        }

        struct B {
            pub foo: Foo1,
        }

        struct Foo {
            pub x: i64,
        }

        struct A {
            pub foo: Foo,
        }

        struct Root {
            pub a: A,
            pub b: B,
        }
    """, """{
  "a": {
    "foo": { "x": 1 }
  },
  "b": {
    "foo": { "y": 2 }
  }
}""")

    fun `test suggest name based on field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Bar {
            pub bar_field: i64,
        }

        struct Foo {
            pub foo_field: i64,
        }

        struct Root {
            pub foo: Foo,
            pub bar: Bar,
        }
    """, """{
  "foo": {
    "foo_field": 1
  },
  "bar": {
    "bar_field": 2
  }
}""")

    fun `test suggest name struct contained in multiple fields`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct Struct {
            pub foo_field: i64,
        }

        struct Root {
            pub foo: Struct,
            pub bar: Struct,
        }
    """, """{
  "foo": {
    "foo_field": 1
  },
  "bar": {
    "foo_field": 2
  }
}""")

    fun `test do not use names existing in file scope`() = doCopyPasteTest("""
        //- lib.rs
        struct Foo;
        struct Root;
        /*caret*/
    """, """
        //- lib.rs
        struct Foo;
        struct Root;

        struct Foo1 {
            pub b: i64,
            pub c: i64,
        }

        struct Root1 {
            pub foo: Foo1,
            pub b: bool,
        }
    """, """{"foo": {"b": 1, "c": 2}, "b": true}""")

    fun `test do not use names existing in mod scope`() = doCopyPasteTest("""
        //- lib.rs
        struct Foo1;
        struct Root1;

        mod foo {
            struct Foo;
            struct Root;
            /*caret*/
        }
    """, """
        //- lib.rs
        struct Foo1;
        struct Root1;

        mod foo {
            struct Foo;
            struct Root;

            struct Foo1 {
                pub b: i64,
                pub c: i64,
            }

            struct Root1 {
                pub foo: Foo1,
                pub b: bool,
            }
        }
    """, """{"foo": {"b": 1, "c": 2}, "b": true}""")

    fun `test suggest name kebab case field`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct FooBar {
            pub field: i64,
        }

        struct Root {
            pub foo_bar: FooBar,
        }
    """, """{"foo-bar": {"field": 1}}""")

    fun `test suggest name invalid field name`() = doCopyPasteTest("""
        //- lib.rs
        /*caret*/
    """, """
        //- lib.rs
        struct _1 {
            pub field: i64,
        }

        struct Root {
            pub _1: _1,
        }
    """, """{"1_": {"field": 1}}""")

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
