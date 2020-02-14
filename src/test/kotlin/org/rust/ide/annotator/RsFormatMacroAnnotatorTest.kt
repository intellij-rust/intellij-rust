/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ide.colors.RsColor

class RsFormatMacroAnnotatorTest : RsAnnotatorTestBase(RsFormatMacroAnnotator::class) {
    override fun setUp() {
        super.setUp()
        super.annotationFixture.registerSeverities(RsColor.values().map(RsColor::testSeverity))
    }

    fun `test invalid 1`() = checkHighlighting("""
        fn main() {
            println!("<error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">{</error>");
            //~^ ERROR invalid format string: expected `'}'` but string was terminated
            println!("<FORMAT_SPECIFIER>{{</FORMAT_SPECIFIER><FORMAT_SPECIFIER>}}</FORMAT_SPECIFIER>");
            println!("<error descr="Invalid format string: unmatched '}'">}</error>");
            //~^ ERROR invalid format string: unmatched `}` found
//            let _ = format!("{_foo}", _foo = 6usize);
            //~^ ERROR invalid format string: invalid argument name `_foo`
//            let _ = format!("{_}", _ = 6usize);
            //~^ ERROR invalid format string: invalid argument name `_`
            let _ = format!("<error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">{</error>");
            //~^ ERROR invalid format string: expected `'}'` but string was terminated
            let _ = format!("<error descr="Invalid format string: unmatched '}'">}</error>");
            //~^ ERROR invalid format string: unmatched `}` found
            let _ = format!("{<error descr="Invalid format string">\</error>\}");
            //~^ ERROR invalid format string: expected `'}'`, found `'\\'`
            let _ = format!("\n\n\n{\n\n<error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">\</error>n");
            //~^ ERROR invalid format string
            let _ = format!(r###"
            <error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">{</error>"###);
            //~^ ERROR invalid format string
            let _ = format!(r###"
            <error descr="Invalid format string: unmatched '}'">}</error>
            "###);
            //~^^^ ERROR invalid format string: unmatched `}` found
        }     """)

    fun `test invalid 2`() = checkHighlighting("""
        fn main() {
            format!("<FORMAT_SPECIFIER>{
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>a</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string
            format!("<FORMAT_SPECIFIER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\
            b</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string
            format!(r#"<FORMAT_SPECIFIER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\
            rawc</FORMAT_SPECIFIER>"#);
            //~^^^ ERROR invalid format string
            format!(r#"<FORMAT_SPECIFIER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>rawd</FORMAT_SPECIFIER>"#);
            //~^^^ ERROR invalid format string
            format!("<FORMAT_SPECIFIER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>e</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string
            format!("
            <FORMAT_SPECIFIER>{
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>a</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string
            format!("
            <FORMAT_SPECIFIER>{
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>a
            </FORMAT_SPECIFIER>");
            //~^^ ERROR invalid format string
            format!(r#"
        raw  <FORMAT_SPECIFIER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\
            c</FORMAT_SPECIFIER>"#);
            //~^^^ ERROR invalid format string
            format!(r#"
        raw  <FORMAT_SPECIFIER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>d</FORMAT_SPECIFIER>"#);
            //~^^^ ERROR invalid format string
            format!("
          <FORMAT_SPECIFIER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>e</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string

            format!("
            <FORMAT_SPECIFIER>{asdf
            }</FORMAT_SPECIFIER>
            ", asdf=1);
            // ok - this is supported
            format!("
            <FORMAT_SPECIFIER>{
            <error descr="Invalid format string">asdf</error>}</FORMAT_SPECIFIER>
            ", asdf=1);
            //~^^ ERROR invalid format string
            println!("\t<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>");
            //~^ ERROR 1 positional argument in format string

            // note: `\x7B` is `{`
            println!("<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>\u{8} <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`">{</error>", 1);
            //~^ ERROR invalid format string: expected `'}'` but string was terminated

            println!("<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>\u8 {", 1);
            //~^ ERROR incorrect unicode escape sequence

            // note: raw strings don't escape `\xFF` and `\u{FF}` sequences
            println!(r#"<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>\u{8} <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`">{</error>"#, 1);
            //~^ ERROR invalid format string: unmatched `}` found
        }
    """)

    fun `test invalid inside`() = checkHighlighting("""
        fn main(){
            println!("{3<error descr="Invalid format string">a</error>}");
            println!("{:<error descr="Invalid format string">|</error>}");
            println!("{:>><error descr="Invalid format string">></error>}");
            println!("{<error descr="Invalid format string">!:?</error>}");
            println!("{:?<error descr="Invalid format string">?</error>}");
        }
    """)

    fun `test valid`() = checkByText("""
        fn main(){
            println!("<FORMAT_SPECIFIER>{{</FORMAT_SPECIFIER><FORMAT_SPECIFIER>}}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{:?}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{x:}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:a}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:a?}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:0<}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:0$}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:00$}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:*<abc}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:10s}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:10$.10s}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{3:a$.b${'$'}s}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{number:*>+#0width$.10x?}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{:-}</FORMAT_SPECIFIER> <FORMAT_SPECIFIER>{:#}</FORMAT_SPECIFIER> <FORMAT_SPECIFIER>{:+#}</FORMAT_SPECIFIER>");
            println!("");
            println!("<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>");
            let mut w = Vec::new();
            write!(&mut w,"<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>",1)
            format_args!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>",1)
        }
    """, checkInfo = true, ignoreExtraHighlighting = true)

}
