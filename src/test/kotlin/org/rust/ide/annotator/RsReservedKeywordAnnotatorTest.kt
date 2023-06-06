/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.SkipTestWrapping

@SkipTestWrapping
class RsReservedKeywordAnnotatorTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {

    fun `test annotate reserved keyword 1`() = checkByText("""
        fn foo() {
            let <error descr="`do` is reserved keyword">do</error> = 123;
            let a: <error descr="`typeof` is reserved keyword">typeof</error> = 1;
        }
        fn <error descr="`unsized` is reserved keyword">unsized</error>() {}

    """)

    fun `test annotate reserved keyword 2`() = checkByText("""
        <error descr="`priv` is reserved keyword">priv</error><error descr="'!' or '::' expected, got 'fn'"> </error>fn bar() {}

        trait Trait {
            <error descr="`virtual` is reserved keyword">virtual</error><error descr="'!' or '::' expected, got 'fn'"> </error>fn foo();
        }

        impl Trait for Struct {
            <error descr="`override` is reserved keyword">override</error><error descr="'!' or '::' expected, got 'type'"> </error>type Type = i32;
            <error descr="`final` is reserved keyword">final</error><error descr="'!' or '::' expected, got 'fn'"> </error>fn foo() {}
        }
    """)

    fun `test annotate reserved keyword in declarative macros`() = checkByText("""
        #![feature(decl_macro)]

        #[macro_export]
        macro_rules! m {
            (final $ x:ident) => { fn $ x() {} };
            (override $ x:ident) => { m!(final $ x) };
        }

        macro m2(priv $ x:ident) {
            m!(final $ x)
        }

        fn main() {
            m!(final foo);
            m!(override bar);
            m2!(priv baz);
            <error descr="`do` is reserved keyword">do</error>!();
            dbg!(<error descr="`abstract` is reserved keyword">abstract</error>);
            println!("Hello! {}", <error descr="`become` is reserved keyword">become</error>);
        }
    """)

    fun `test annotate reserved keyword in attributes`() = checkByText("""
        #![feature(do)] // Should be annotated

        #[<error descr="`final` is reserved keyword">final</error>]
        fn foo() {}

        #[proc_macro(virtual)]
        fn bar() {}


        #[derive(typeof)] // Should be annotated
        struct Struct {
            #[<error descr="`unsized` is reserved keyword">unsized</error>] field1: (),
            #[helper(virtual)] field2: (),
            #[helper { abstract }] field3: (),
        }
    """)

    fun `test escape reserved keyword fix 1`() = checkFixByText("Escape reserved keyword", """
        fn foo() {
            let <error descr="`final` is reserved keyword">final/*caret*/</error> = 123;
        }
    """, """
        fn foo() {
            let r#final/*caret*/ = 123;
        }
    """)

    fun `test escape reserved keyword fix 2`() = checkFixByText("Escape reserved keyword", """
        fn <error descr="`virtual` is reserved keyword">virtual/*caret*/</error>() {}
    """, """
        fn r#virtual/*caret*/() {}
    """)

    fun `test no escape reserved keyword fix`() = checkFixIsUnavailable("", """
        fn foo() {
            let a: <error descr="`abstract` is reserved keyword">abstract/*caret*/</error> = 1;
        }
    """)
}
