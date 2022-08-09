/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.colors.RsColor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsFormatMacroAnnotatorTest : RsAnnotatorTestBase(RsFormatMacroAnnotator::class) {
    override fun setUp() {
        super.setUp()
        super.annotationFixture.registerSeverities(RsColor.values().map(RsColor::testSeverity))
    }

    private val implDisplayI32 = """
        use std::fmt;
        impl fmt::Display for i32 {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { unimplemented!() }
        }
    """

    fun `test missing argument`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("<error descr="Invalid reference to positional argument 0 (no arguments were given)">{}</error>");
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<error descr="Invalid reference to positional argument 1 (there is 1 argument)">1</error>}</FORMAT_PARAMETER>", 1);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>1</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<error descr="Invalid reference to positional argument 3 (there are 2 arguments)">3</error>}</FORMAT_PARAMETER>", 1, 1);
            println!("Hello <FORMAT_PARAMETER>{:<error descr="Invalid reference to positional argument 1 (there is 1 argument)">1${'$'}</error>}</FORMAT_PARAMETER>", 1);
        }
    """)

    // TODO: the plugin should highlight unknown argument even if `format_args_capture` is available
    @MockRustcVersion("1.57.0-nightly")
    fun `test missing explicit arguments 1`() = checkErrors("""
        #![feature(format_args_capture)]

        $implDisplayI32

        println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>");
        println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>foo${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1);
    """)

    // TODO: the plugin should highlight unknown argument even if `format_args_capture` is available
    @MinRustcVersion("1.58.0-nightly")
    fun `test missing explicit arguments 2`() = checkErrors("""
        $implDisplayI32

        println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>");
        println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>foo${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1);
    """)

    @MockRustcVersion("1.50.0-nightly")
    fun `test implicit named arguments 1`() = checkErrors("""
        #![feature(format_args_capture)]

        fn main() {
            let foo = 1;
            println!("Hello <FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>");
        }
    """)

    @MinRustcVersion("1.58.0-nightly")
    fun `test implicit named arguments 2`() = checkErrors("""
        fn main() {
            let foo = 1;
            println!("Hello <FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>");
        }
    """)

    fun `test missing parameter`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("", <error descr="Argument never used">1.2</error>);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, <error descr="Argument never used">1.2</error>);
            println!("", <error descr="Named argument never used">foo=1.2</error>);
        }
    """)

    fun `test invalid parameter type`() = checkErrors("""
        fn main() {
            println!("<FORMAT_PARAMETER>{:<error descr="Unknown format trait `u`">u</error>}</FORMAT_PARAMETER>", 1);
        }
    """)

    fun `test argument matches parameter`() = checkErrors("""
        $implDisplayI32

        struct Debug;
        impl std::fmt::Debug for Debug {
            fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result { unimplemented!() }
        }

        fn main() {
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", 1);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", 1);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", foo=1);
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER><FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", 1, 1);
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", foo=1);

            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", foo=1);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>1</FORMAT_SPECIFIER>:<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, Debug);

            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", foo=1);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>bar</FORMAT_SPECIFIER>:<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", foo=1, bar=Debug);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", foo=1);
        }
    """)

    fun `test invalid syntax 1`() = checkErrors("""
        fn main() {
            println!("<error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">{</error>");
            //~^ ERROR invalid format string: expected `'}'` but string was terminated
            println!("<FORMAT_PARAMETER>{{</FORMAT_PARAMETER><FORMAT_PARAMETER>}}</FORMAT_PARAMETER>");
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
            let _ = format!("<FORMAT_PARAMETER>{<error descr="Invalid format string">\</error>\}</FORMAT_PARAMETER>");
            //~^ ERROR invalid format string: expected `'}'`, found `'\\'`
            let _ = format!("\n\n\n<FORMAT_PARAMETER>{\n\n<error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">\</error></FORMAT_PARAMETER>n");
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

    fun `test invalid syntax 2`() = checkErrors("""
        $implDisplayI32

        fn main() {
            format!("<FORMAT_PARAMETER>{
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>a</FORMAT_PARAMETER>");
            //~^ ERROR invalid format string
            format!("<FORMAT_PARAMETER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\
            b</FORMAT_PARAMETER>");
            //~^ ERROR invalid format string
            format!(r#"<FORMAT_PARAMETER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\
            rawc</FORMAT_PARAMETER>"#);
            //~^^^ ERROR invalid format string
            format!(r#"<FORMAT_PARAMETER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\n
        \n
           rawd</FORMAT_PARAMETER>"#);
            //~^^^ ERROR invalid format string
            format!("<FORMAT_PARAMETER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>e</FORMAT_PARAMETER>");
            //~^ ERROR invalid format string
            format!("
            <FORMAT_PARAMETER>{
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>a</FORMAT_PARAMETER>");
            //~^ ERROR invalid format string
            format!("
            <FORMAT_PARAMETER>{
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>a
            </FORMAT_PARAMETER>");
            //~^^ ERROR invalid format string
            format!(r#"
        raw  <FORMAT_PARAMETER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\
            c</FORMAT_PARAMETER>"#);
            //~^^^ ERROR invalid format string
            format!(r#"
        raw  <FORMAT_PARAMETER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\n
        \n
           d</FORMAT_PARAMETER>"#);
            //~^^^ ERROR invalid format string
            format!("
          <FORMAT_PARAMETER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>e</FORMAT_PARAMETER>");
            //~^ ERROR invalid format string

            format!("
            <FORMAT_PARAMETER>{<FORMAT_SPECIFIER>asdf</FORMAT_SPECIFIER>
            }</FORMAT_PARAMETER>
            ", asdf=1);
            // ok - this is supported
            format!("
            <FORMAT_PARAMETER>{
            <error descr="Invalid format string">asdf</error>}</FORMAT_PARAMETER>
            ", asdf=1);
            //~^^ ERROR invalid format string

            // note: `\x7B` is `{`
            println!("<FORMAT_PARAMETER>\x7B}</FORMAT_PARAMETER>\u{8} <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`">{</error>", 1);
            //~^ ERROR invalid format string: expected `'}'` but string was terminated

            println!("<FORMAT_PARAMETER>\x7B}</FORMAT_PARAMETER>\u8 {", 1);
            //~^ ERROR incorrect unicode escape sequence

            // note: raw strings don't escape `\xFF` and `\u{FF}` sequences
            println!(r#"\x7B<error descr="Invalid format string: unmatched '}'">}</error>\u<FORMAT_PARAMETER>{8}</FORMAT_PARAMETER> <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`">{</error>"#, 1);
            //~^ ERROR invalid format string: unmatched `}` found
        }
    """)

    fun `test invalid inner syntax`() = checkErrors("""
        fn main() {
            println!("<FORMAT_PARAMETER>{3<error descr="Invalid format string">a</error>}</FORMAT_PARAMETER>");
            println!("<FORMAT_PARAMETER>{:<error descr="Invalid format string">|</error>}</FORMAT_PARAMETER>");
            println!("<FORMAT_PARAMETER>{:>><error descr="Invalid format string">></error>}</FORMAT_PARAMETER>");
            println!("<FORMAT_PARAMETER>{<error descr="Invalid format string">!:?</error>}</FORMAT_PARAMETER>");
            println!("<FORMAT_PARAMETER>{:?<error descr="Invalid format string">?</error>}</FORMAT_PARAMETER>");
        }
    """)

    fun `test valid syntax`() = checkErrors("""
        use std::fmt;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::Debug for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::LowerExp for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::LowerHex for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }

        fn main(){
            println!("<FORMAT_PARAMETER>{{</FORMAT_PARAMETER><FORMAT_PARAMETER>}}</FORMAT_PARAMETER>");
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:#<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>e</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>x</FORMAT_SPECIFIER>:}</FORMAT_PARAMETER>", x=S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:<FUNCTION>x</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:<FUNCTION>x?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:0<}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>1</FORMAT_SPECIFIER>:<FORMAT_SPECIFIER>0${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>1</FORMAT_SPECIFIER>:0<FORMAT_SPECIFIER>0${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:<FORMAT_SPECIFIER>10</FORMAT_SPECIFIER><FUNCTION>x</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>.<FORMAT_SPECIFIER>10</FORMAT_SPECIFIER><FUNCTION>x</FUNCTION>}</FORMAT_PARAMETER>", S, 1);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:<FORMAT_SPECIFIER>a${'$'}</FORMAT_SPECIFIER>.<FORMAT_SPECIFIER>b${'$'}</FORMAT_SPECIFIER><FUNCTION>x</FUNCTION>}</FORMAT_PARAMETER>", S, a=2, b=3);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>number</FORMAT_SPECIFIER>:*>+#0<FORMAT_SPECIFIER>width${'$'}</FORMAT_SPECIFIER>.<FORMAT_SPECIFIER>10</FORMAT_SPECIFIER><FUNCTION>x?</FUNCTION>}</FORMAT_PARAMETER>", number=S, width=1);
            println!("<FORMAT_PARAMETER>{:-}</FORMAT_PARAMETER> <FORMAT_PARAMETER>{:#}</FORMAT_PARAMETER> <FORMAT_PARAMETER>{:+#}</FORMAT_PARAMETER>", S, S, S);
            println!("");
            println!("<FORMAT_PARAMETER>\x7B}</FORMAT_PARAMETER>", S);
            let mut w = Vec::new();
            write!(&mut w, "<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
            format_args!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
        }
    """)

    fun `test format trait is not implemented`() = checkErrors("""
        struct S;

        fn main() {
            let s = S;
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Display` (required by {})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Debug` (required by {:?})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>x?</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Debug` (required by {:x?})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>X?</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Debug` (required by {:X?})">s</error>);
            println!("<FORMAT_PARAMETER>{:#<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Debug` (required by {:#?})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>o</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Octal` (required by {:o})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>x</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `LowerHex` (required by {:x})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>X</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `UpperHex` (required by {:X})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>p</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Pointer` (required by {:p})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>b</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `Binary` (required by {:b})">s</error>);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>e</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `LowerExp` (required by {:e})">s</error>);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:<FUNCTION>E</FUNCTION>}</FORMAT_PARAMETER>", <error descr="`S` doesn't implement `UpperExp` (required by {0:E})">s</error>);
        }
    """)

    fun `test format trait is implemented`() = checkErrors("""
        use std::fmt;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::Debug for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::Octal for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::LowerHex for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::UpperHex for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::Pointer for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::Binary for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::LowerExp for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        impl fmt::UpperExp for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>x?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>X?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:#<FUNCTION>?</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>o</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>x</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>X</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>p</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>b</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>e</FUNCTION>}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{:<FUNCTION>E</FUNCTION>}</FORMAT_PARAMETER>", S);
        }
    """)

    fun `test format trait is implemented behind deref`() = checkErrors("""
        use std::fmt;
        use std::ops::Deref;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { unimplemented!() }
        }

        struct W { s: S }

        impl Deref for W {
            type Target = S;
            fn deref(&self) -> &Self::Target { &self.s }
        }

        fn main() {
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", *W { s: S });
        }
    """)

    fun `test format trait is implemented behind reference`() = checkErrors("""
        $implDisplayI32

        fn main() {
            let s = S;
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", &1);
        }
    """)

    fun `test match format parameters`() = checkErrors("""
        $implDisplayI32

        fn main() {
            let a = 5;
            println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, a);

            println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, 5);
            println!("Hello <FORMAT_PARAMETER>{<FORMAT_SPECIFIER>1</FORMAT_SPECIFIER>:<FORMAT_SPECIFIER>0${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 5, 1);
            println!("Hello <FORMAT_PARAMETER>{<FORMAT_SPECIFIER>1</FORMAT_SPECIFIER>:<FORMAT_SPECIFIER>0${'$'}</FORMAT_SPECIFIER>.<FORMAT_SPECIFIER>2${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, 1, 4);
            println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>width${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, width = 5);
        }
    """)

    fun `test check format parameters type`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, <error descr="Width specifier must be of type `usize`">"asd"</error>);
            println!("Hello <FORMAT_PARAMETER>{:.<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, <error descr="Precision specifier must be of type `usize`">2.0</error>);
            println!("Hello <FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>.<FORMAT_SPECIFIER>1${'$'}</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 1, <error descr="Precision specifier must be of type `usize`"><error descr="Width specifier must be of type `usize`">2.0</error></error>);
        }
    """)

    fun `test check precision asterisk`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("<FORMAT_PARAMETER>{:.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, 2);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1);
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>name</FORMAT_SPECIFIER>:.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, 3, name=2);
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER><FORMAT_PARAMETER>{<FORMAT_SPECIFIER>2</FORMAT_SPECIFIER>:.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", 1, 5, 2);
            println!("<FORMAT_PARAMETER>{:<FORMAT_SPECIFIER>a${'$'}</FORMAT_SPECIFIER>.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", 5, 1, a=3);
        }
    """)

    fun `test check precision asterisk wrong parameter`() = checkErrors("""
        use std::fmt;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            println!("<FORMAT_PARAMETER>{:.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", <error descr="Precision specifier must be of type `usize`">"asd"</error>, S);
            println!("<FORMAT_PARAMETER>{<FORMAT_SPECIFIER>0</FORMAT_SPECIFIER>:.<FORMAT_SPECIFIER>*</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>!", <error descr="Precision specifier must be of type `usize`">S</error>);
        }
    """)

    fun `test ignore unknown types`() = checkErrors("""
        struct G<T>(T);
        impl<T> fmt::Display for G<T> {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            let g: G<S> = G(S);
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", g);
        }
    """)

    fun `test ignore never type`() = checkErrors("""
        fn never() -> ! { unimplemented!() }

        fn main() {
            println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", never());
        }
    """)

    fun `test do not highlight non stdlib macros`() = checkErrors("""
        macro_rules! println {
            ($ e:expr, $ i:ident) => {};
        }
        fn main() {
            println!("Hello", x);
            println!("{} Hello {}", x);
        }
    """)

    fun `test raw format string`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!(r"\<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>!", 1);
            println!(r"\u<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>!", 1);
            println!(r##"<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>

            <error descr="Invalid reference to positional argument 1 (there is 1 argument)">{}</error>"##, 1);
        }
    """)

    fun `test ignore byte format string`() = checkErrors("""
        fn main() {
            println!(b"format", 1);
            println!(br"format", 1);
            println!(br##"format"##, 1);
        }
    """)

    @BatchMode
    fun `test only errors in batch mode`() = checkErrors("""
        struct S;

        fn main() {
            let s = S;
            println!("{}", <error descr="`S` doesn't implement `Display` (required by {})">s</error>);
            println!("{:?}", <error descr="`S` doesn't implement `Debug` (required by {:?})">s</error>);
            println!("{:x?}", <error descr="`S` doesn't implement `Debug` (required by {:x?})">s</error>);
            println!("{:X?}", <error descr="`S` doesn't implement `Debug` (required by {:X?})">s</error>);
            println!("{:#?}", <error descr="`S` doesn't implement `Debug` (required by {:#?})">s</error>);
            println!("{:o}", <error descr="`S` doesn't implement `Octal` (required by {:o})">s</error>);
            println!("{:x}", <error descr="`S` doesn't implement `LowerHex` (required by {:x})">s</error>);
            println!("{:X}", <error descr="`S` doesn't implement `UpperHex` (required by {:X})">s</error>);
            println!("{:p}", <error descr="`S` doesn't implement `Pointer` (required by {:p})">s</error>);
            println!("{:b}", <error descr="`S` doesn't implement `Binary` (required by {:b})">s</error>);
            println!("{:e}", <error descr="`S` doesn't implement `LowerExp` (required by {:e})">s</error>);
            println!("{0:E}", <error descr="`S` doesn't implement `UpperExp` (required by {0:E})">s</error>);
        }
    """)

    fun `test ignore format string macro argument`() = checkErrors("""
        struct S;

        fn main() {
            println!(concat!("{}", "{}", "{}"), S, S);
        }
    """)

    fun `test fqn macro call`() = checkErrors("""
        use std::fmt;
        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }
        fn main() {
            std::println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
            std::println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER> <error descr="Invalid reference to positional argument 1 (there is 1 argument)">{}</error>", S);
            std::println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S, <error descr="Argument never used">S</error>);
        }
    """)

    fun `test custom debug macro`() = checkErrors("""
        macro_rules! debug {
            (${'$'}a:expr, ${'$'}b:expr) => {}
        }

        fn main() {
            debug!("{}", 1);
        }
    """)

    fun `test panic macro`() = checkErrors("""
        use std::fmt;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            panic!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
            panic!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER> <error descr="Invalid reference to positional argument 1 (there is 1 argument)">{}</error>", S);
            panic!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S, <error descr="Argument never used">S</error>);
        }
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test panic with single literal`() = checkErrors("""
        fn main() {
            panic!("{}");
        }
    """)

    @MockEdition(Edition.EDITION_2021)
    fun `test panic macro 2021`() = checkErrors("""
        use std::fmt;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            panic!("<error descr="Invalid reference to positional argument 0 (no arguments were given)">{}</error>");
            panic!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S);
            panic!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER> <error descr="Invalid reference to positional argument 1 (there is 1 argument)">{}</error>", S);
            panic!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", S, <error descr="Argument never used">S</error>);
        }
    """)

    @ExpandMacros
    fun `test custom macro`() = checkErrors("""
        macro_rules! as_is { ($($ t:tt)*) => {$($ t)*}; }
        fn main() {
            as_is! {
                println!("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER>", 1);
                println!("", <error descr="Argument never used">1</error>);
            }
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test no highlighting in cfg-disabled code`() = checkErrors("""
        $implDisplayI32

        #[cfg(not(intellij_rust))]
        fn foo() {
            println!("{}");
            println!("{0}{1}", 1);
            println!("{0}{1}{3}", 1, 1);
            println!("Hello {:1${'$'}}", 1);
        }

        fn bar() {
            #[cfg(not(intellij_rust))]
            println!("{}");
        }
    """)
}
