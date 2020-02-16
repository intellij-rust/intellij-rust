/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsExpressionAnnotator

class AddStructFieldsFixTest : RsAnnotatorTestBase(RsExpressionAnnotator::class) {
    fun `test no named fields`() = checkBothQuickFix("""
        struct S { foo: i32, bar: f64 }

        fn main() {
            let _ = <error>S</error> { /*caret*/ };
        }
    """, """
        struct S { foo: i32, bar: f64 }

        fn main() {
            let _ = S { foo: 0/*caret*/, bar: 0.0 };
        }
    """)

    fun `test no positional fields`() = checkBothQuickFix("""
        struct S(i32, f64);

        fn main() {
            let _ = <error>S</error> { /*caret*/ };
        }
    """, """
        struct S(i32, f64);

        fn main() {
            let _ = S { 0: 0/*caret*/, 1: 0.0 };
        }
    """)

    fun `test aliased struct`() = checkBothQuickFix("""
        struct S { foo: i32, bar: f64 }
        type T = S;

        fn main() {
            let _ = <error>T</error> { /*caret*/ };
        }
    """, """
        struct S { foo: i32, bar: f64 }
        type T = S;

        fn main() {
            let _ = T { foo: 0/*caret*/, bar: 0.0 };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no comma`() = checkBothQuickFix("""
        struct S { a: i32, b: String }

        fn main() {
            <error>S</error> { a: 92/*caret*/};
        }
    """, """
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, b: "".to_string()/*caret*/ };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test with comma`() = checkBothQuickFix("""
        struct S { a: i32, b: String }

        fn main() {
            <error>S</error> { a: 92, /*caret*/};
        }
    """, """
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, b: "".to_string()/*caret*/ };
        }
    """)

    fun `test some existing fields`() = checkBothQuickFix("""
        struct S(i32, i32, i32, i32);

        fn main() {
            let _ = <error>S</error> {
                0: 92,
                2: 92/*caret*/
            };
        }
    """, """
        struct S(i32, i32, i32, i32);

        fn main() {
            let _ = S {
                0: 92,
                1: 0/*caret*/,
                2: 92,
                3: 0
            };
        }
    """)

    fun `test first field is added first`() = checkBothQuickFix("""
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = <error>S</error> { b: 0,/*caret*/ };
        }
    """, """
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { a: 0/*caret*/, b: 0, };
        }
    """)

    fun `test last field is added last`() = checkBothQuickFix("""
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = <error>S</error> { /*caret*/a: 0 };
        }
    """, """
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { a: 0, b: 0/*caret*/ };
        }
    """)

    fun `test preserves order`() = checkBothQuickFix("""
        struct S { a: i32, b: i32, c: i32, d: i32, e: i32}

        fn main() {
            let _ = <error>S</error> { a: 0, c: 1, e: 2/*caret*/ };
        }
    """, """
        struct S { a: i32, b: i32, c: i32, d: i32, e: i32}

        fn main() {
            let _ = S { a: 0, b: 0/*caret*/, c: 1, d: 0, e: 2 };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test issue 980`() = checkBothQuickFix("""
        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
        }

        fn main() {
            <error>Mesh</error>{/*caret*/};
        }
    """, """
        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
        }

        fn main() {
            Mesh{
                name: "".to_string(),
                vertices: vec![],
                faces: vec![],
                material: None
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test many type fields`() = checkBothQuickFix("""
        type AliasedString = String;
        struct TrivialStruct;
        struct EmptyTupleStruct();
        struct EmptyStruct {}

        struct DataContainer<'a > {
            bool_field: bool,
            char_field: char,
            i8_field: i8,
            i16_field: i16,
            i32_field: i32,
            i64_field: i64,
            u8_field: u8,
            u16_field: u16,
            u32_field: u32,
            u64_field: u64,
            isize_field: isize,
            usize_field: usize,
            f32_field: f32,
            f64_field: f64,
            slice_field: [i32],
            array_field: [i32; 3],
            str_field: String,
            vec_field: Vec<i32>,
            opt_field: Option<i32>,
            ref_field: &'a String,
            ref_mut_field: &'a mut String,
            tuple_field: (bool, char, i8, String),
            aliased_field: AliasedString,
            trivial_struct: TrivialStruct,
            empty_tuple_struct: EmptyTupleStruct,
            empty_struct: EmptyStruct,
            unsupported_type_field: fn(i32) -> i32
        }

        fn main() {
            <error>DataContainer</error>{/*caret*/};
        }
    """, """
        type AliasedString = String;
        struct TrivialStruct;
        struct EmptyTupleStruct();
        struct EmptyStruct {}

        struct DataContainer<'a > {
            bool_field: bool,
            char_field: char,
            i8_field: i8,
            i16_field: i16,
            i32_field: i32,
            i64_field: i64,
            u8_field: u8,
            u16_field: u16,
            u32_field: u32,
            u64_field: u64,
            isize_field: isize,
            usize_field: usize,
            f32_field: f32,
            f64_field: f64,
            slice_field: [i32],
            array_field: [i32; 3],
            str_field: String,
            vec_field: Vec<i32>,
            opt_field: Option<i32>,
            ref_field: &'a String,
            ref_mut_field: &'a mut String,
            tuple_field: (bool, char, i8, String),
            aliased_field: AliasedString,
            trivial_struct: TrivialStruct,
            empty_tuple_struct: EmptyTupleStruct,
            empty_struct: EmptyStruct,
            unsupported_type_field: fn(i32) -> i32
        }

        fn main() {
            DataContainer{
                bool_field: false,
                char_field: '',
                i8_field: 0,
                i16_field: 0,
                i32_field: 0,
                i64_field: 0,
                u8_field: 0,
                u16_field: 0,
                u32_field: 0,
                u64_field: 0,
                isize_field: 0,
                usize_field: 0,
                f32_field: 0.0,
                f64_field: 0.0,
                slice_field: [],
                array_field: [],
                str_field: "".to_string(),
                vec_field: vec![],
                opt_field: None,
                ref_field: &"".to_string(),
                ref_mut_field: &mut "".to_string(),
                tuple_field: (false, '', 0, "".to_string()),
                aliased_field: "".to_string(),
                trivial_struct: TrivialStruct,
                empty_tuple_struct: EmptyTupleStruct(),
                empty_struct: EmptyStruct {},
                unsupported_type_field: ()
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test smart pointers`() = checkBothQuickFix("""
        use std::rc::Rc;
        use std::sync::{Arc, Mutex};
        use std::cell::{Cell, RefCell, UnsafeCell};

        struct S<'a> {
            a: Box<Box<u32>>,
            b: Rc<&'a u64>,
            c: Arc<String>,
            d: Cell<f32>,
            e: RefCell<u32>,
            f: UnsafeCell<u64>,
            g: Mutex<String>
        }

        fn main() {
            <error>S</error>{/*caret*/};
        }
    """, """
        use std::rc::Rc;
        use std::sync::{Arc, Mutex};
        use std::cell::{Cell, RefCell, UnsafeCell};

        struct S<'a> {
            a: Box<Box<u32>>,
            b: Rc<&'a u64>,
            c: Arc<String>,
            d: Cell<f32>,
            e: RefCell<u32>,
            f: UnsafeCell<u64>,
            g: Mutex<String>
        }

        fn main() {
            S{
                a: Box::new(Box::new(0)),
                b: Rc::new(&0),
                c: Arc::new("".to_string()),
                d: Cell::new(0.0),
                e: RefCell::new(0),
                f: UnsafeCell::new(0),
                g: Mutex::new("".to_string())
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test default`() = checkBothQuickFix("""
        #[derive(Default)]
        struct A {
            a: u64
        }

        struct B {
            b: u64
        }

        impl Default for B {
            fn default() -> Self {
                Self { b: 1 }
            }
        }

        struct S {
            a: A,
            b: B
        }

        fn main() {
            <error>S</error>{/*caret*/};
        }
    """, """
        #[derive(Default)]
        struct A {
            a: u64
        }

        struct B {
            b: u64
        }

        impl Default for B {
            fn default() -> Self {
                Self { b: 1 }
            }
        }

        struct S {
            a: A,
            b: B
        }

        fn main() {
            S{ a: Default::default(), b: Default::default() };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test local variable`() = checkBothQuickFix("""
        struct A {
            x: u64
        }
        struct S<'a> {
            a: u64,
            b: &'a u64,
            c: &'a &'a mut u64,
            d: u32,
            e: u64,
            f: String,
            obj: A
        }

        fn test(a: u64) {
            let b = 3 as u64;
            let mut c: u64 = 1;
            let d: u64 = 5;
            {
                let e: u64 = 5;
            }
            let f = "".to_string();
            let obj = A { x: 5 };

            <error>S</error>{/*caret*/};
        }
    """, """
        struct A {
            x: u64
        }
        struct S<'a> {
            a: u64,
            b: &'a u64,
            c: &'a &'a mut u64,
            d: u32,
            e: u64,
            f: String,
            obj: A
        }

        fn test(a: u64) {
            let b = 3 as u64;
            let mut c: u64 = 1;
            let d: u64 = 5;
            {
                let e: u64 = 5;
            }
            let f = "".to_string();
            let obj = A { x: 5 };

            S{
                a,
                b: &b,
                c: &&mut c,
                d: 0,
                e: 0,
                f,
                obj
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test local variable recursive`() = checkRecursiveQuickFix("""
        struct A {
            a: u64
        }

        struct B {
            a: A
        }

        fn main() {
            let a: u64 = 5;
            <error>B</error>{/*caret*/};
        }
    """, """
        struct A {
            a: u64
        }

        struct B {
            a: A
        }

        fn main() {
            let a: u64 = 5;
            B{ a: A { a } };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test 1-level recursively fill struct`() = checkRecursiveQuickFix("""
        union Union {
            a: i32,
            b: f32
        }

        enum Enum {
            A { a: i32 },
            B(i32),
            C
        }

        struct TupleStruct(i32, i32);

        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub un: Union,
            pub en: Enum,
            pub metadata: MetaData,
            pub tupleStruct: TupleStruct
        }

        fn main() {
            <error>Mesh</error>{/*caret*/};
        }
    """, """
        union Union {
            a: i32,
            b: f32
        }

        enum Enum {
            A { a: i32 },
            B(i32),
            C
        }

        struct TupleStruct(i32, i32);

        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub un: Union,
            pub en: Enum,
            pub metadata: MetaData,
            pub tupleStruct: TupleStruct
        }

        fn main() {
            Mesh{
                name: "".to_string(),
                vertices: vec![],
                faces: vec![],
                material: None,
                un: (),
                en: Enum::C,
                metadata: MetaData {
                    author: "".to_string(),
                    licence: None,
                    specVersion: 0
                },
                tupleStruct: TupleStruct(0, 0)
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test 2-level recursively fill struct`() = checkRecursiveQuickFix("""
        struct ToolInfo {
            name: String,
            toolVersion: String,
        }

        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32,
            tool: ToolInfo
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub metadata: MetaData
        }

        fn main() {
            <error>Mesh</error>{/*caret*/};
        }
    """, """
        struct ToolInfo {
            name: String,
            toolVersion: String,
        }

        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32,
            tool: ToolInfo
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub metadata: MetaData
        }

        fn main() {
            Mesh{
                name: "".to_string(),
                vertices: vec![],
                faces: vec![],
                material: None,
                metadata: MetaData {
                    author: "".to_string(),
                    licence: None,
                    specVersion: 0,
                    tool: ToolInfo { name: "".to_string(), toolVersion: "".to_string() }
                }
            };
        }
    """)

    fun `test we don't filling struct that can't be instantiated (has private fields)`() = checkRecursiveQuickFix("""
        mod foo {
            pub struct Outer {
                pub inner: Inner,
                pub field2: i32
            }

            pub struct Inner {
                field1: i32,
                field2: i32
            }
        }

        fn main() {
            <error>foo::Outer</error> {/*caret*/};
        }
    """, """
        mod foo {
            pub struct Outer {
                pub inner: Inner,
                pub field2: i32
            }

            pub struct Inner {
                field1: i32,
                field2: i32
            }
        }

        fn main() {
            foo::Outer { inner: (), field2: 0 };
        }
    """)

    fun `test raw identifier field`() = checkBothQuickFix("""
        struct S { r#type: i32 }

        fn main() {
            <error>S</error> { /*caret*/ };
        }
    """, """
        struct S { r#type: i32 }

        fn main() {
            S { r#type: 0/*caret*/ };
        }
    """)

    fun `test raw identifier field local variable`() = checkBothQuickFix("""
        struct S { r#type: i32 }

        fn main() {
            let r#type: i32 = 0;
            <error>S</error> { /*caret*/ };
        }
    """, """
        struct S { r#type: i32 }

        fn main() {
            let r#type: i32 = 0;
            S { r#type };
        }
    """)

    fun `test raw identifier field unnecessary escape`() = checkBothQuickFix("""
        struct S { r#foo: i32, bar: i32 }

        fn main() {
            let foo: i32 = 0;
            let r#bar: i32 = 0;
            <error>S</error> { /*caret*/ };
        }
    """, """
        struct S { r#foo: i32, bar: i32 }

        fn main() {
            let foo: i32 = 0;
            let r#bar: i32 = 0;
            S { foo, bar };
        }
    """)

    private fun checkBothQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkFixByText("Add missing fields", before, after)
        checkFixByText("Recursively add missing fields", before, after)
    }

    private fun checkRecursiveQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkFixByText("Recursively add missing fields", before, after)

}
