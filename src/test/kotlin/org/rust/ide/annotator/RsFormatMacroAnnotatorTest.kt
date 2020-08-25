/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.BatchMode
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
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
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<error descr="Invalid reference to positional argument 1 (there is 1 argument)">1</error>}</FORMAT_SPECIFIER>", 1);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>1</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<error descr="Invalid reference to positional argument 3 (there are 2 arguments)">3</error>}</FORMAT_SPECIFIER>", 1, 1);
            println!("Hello <FORMAT_SPECIFIER>{:<error descr="Invalid reference to positional argument 1 (there is 1 argument)">1${'$'}</error>}</FORMAT_SPECIFIER>", 1);
            println!("<FORMAT_SPECIFIER>{<error descr="There is no argument named `foo`">foo</error>}</FORMAT_SPECIFIER>");
            println!("Hello <FORMAT_SPECIFIER>{:<error descr="There is no argument named `foo`">foo${'$'}</error>}</FORMAT_SPECIFIER>", 1);
        }
    """)

    fun `test missing parameter`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("", <error descr="Argument never used">1.2</error>);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, <error descr="Argument never used">1.2</error>);
            println!("", <error descr="Named argument never used">foo=1.2</error>);
        }
    """)

    fun `test invalid parameter type`() = checkErrors("""
        fn main() {
            println!("<FORMAT_SPECIFIER>{:<error descr="Unknown format trait `u`">u</error>}</FORMAT_SPECIFIER>", 1);
        }
    """)

    fun `test argument matches parameter`() = checkErrors("""
        $implDisplayI32

        struct Debug;
        impl std::fmt::Debug for Debug {
            fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result { unimplemented!() }
        }

        fn main() {
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", 1);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", 1);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>foo</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", foo=1);
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", 1, 1);
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", foo=1);

            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER>", foo=1);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>1</IDENTIFIER>:<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, Debug);

            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>foo</IDENTIFIER>}</FORMAT_SPECIFIER>", foo=1);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>bar</IDENTIFIER>:<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>foo</IDENTIFIER>}</FORMAT_SPECIFIER>", foo=1, bar=Debug);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>foo</IDENTIFIER>}</FORMAT_SPECIFIER>", foo=1);
        }
    """)

    fun `test invalid syntax 1`() = checkErrors("""
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
            let _ = format!("<FORMAT_SPECIFIER>{<error descr="Invalid format string">\</error>\}</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string: expected `'}'`, found `'\\'`
            let _ = format!("\n\n\n<FORMAT_SPECIFIER>{\n\n<error descr="Invalid format string: } expected.
If you intended to print `{` symbol, you can escape it using `{{`">\</error></FORMAT_SPECIFIER>n");
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
            format!(r#"<FORMAT_SPECIFIER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\n
        \n
           rawd</FORMAT_SPECIFIER>"#);
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
        raw  <FORMAT_SPECIFIER>{<error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>\n
        \n
           d</FORMAT_SPECIFIER>"#);
            //~^^^ ERROR invalid format string
            format!("
          <FORMAT_SPECIFIER>{ \n
        \n
           <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`"> </error>e</FORMAT_SPECIFIER>");
            //~^ ERROR invalid format string

            format!("
            <FORMAT_SPECIFIER>{<IDENTIFIER>asdf</IDENTIFIER>
            }</FORMAT_SPECIFIER>
            ", asdf=1);
            // ok - this is supported
            format!("
            <FORMAT_SPECIFIER>{
            <error descr="Invalid format string">asdf</error>}</FORMAT_SPECIFIER>
            ", asdf=1);
            //~^^ ERROR invalid format string

            // note: `\x7B` is `{`
            println!("<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>\u{8} <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`">{</error>", 1);
            //~^ ERROR invalid format string: expected `'}'` but string was terminated

            println!("<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>\u8 {", 1);
            //~^ ERROR incorrect unicode escape sequence

            // note: raw strings don't escape `\xFF` and `\u{FF}` sequences
            println!(r#"\x7B<error descr="Invalid format string: unmatched '}'">}</error>\u<FORMAT_SPECIFIER>{8}</FORMAT_SPECIFIER> <error descr="Invalid format string: } expected.
        If you intended to print `{` symbol, you can escape it using `{{`">{</error>"#, 1);
            //~^ ERROR invalid format string: unmatched `}` found
        }
    """)

    fun `test invalid inner syntax`() = checkErrors("""
        fn main() {
            println!("<FORMAT_SPECIFIER>{3<error descr="Invalid format string">a</error>}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{:<error descr="Invalid format string">|</error>}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{:>><error descr="Invalid format string">></error>}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{<error descr="Invalid format string">!:?</error>}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{:?<error descr="Invalid format string">?</error>}</FORMAT_SPECIFIER>");
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
            println!("<FORMAT_SPECIFIER>{{</FORMAT_SPECIFIER><FORMAT_SPECIFIER>}}</FORMAT_SPECIFIER>");
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:#<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>e</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>x</IDENTIFIER>:}</FORMAT_SPECIFIER>", x=S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:<FUNCTION>x</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:<FUNCTION>x?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:0<}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>1</IDENTIFIER>:<IDENTIFIER>0${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>1</IDENTIFIER>:0<IDENTIFIER>0${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:<IDENTIFIER>10</IDENTIFIER><FUNCTION>x</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:<IDENTIFIER>1${'$'}</IDENTIFIER>.<IDENTIFIER>10</IDENTIFIER><FUNCTION>x</FUNCTION>}</FORMAT_SPECIFIER>", S, 1);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:<IDENTIFIER>a${'$'}</IDENTIFIER>.<IDENTIFIER>b${'$'}</IDENTIFIER><FUNCTION>x</FUNCTION>}</FORMAT_SPECIFIER>", S, a=2, b=3);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>number</IDENTIFIER>:*>+#0<IDENTIFIER>width${'$'}</IDENTIFIER>.<IDENTIFIER>10</IDENTIFIER><FUNCTION>x?</FUNCTION>}</FORMAT_SPECIFIER>", number=S, width=1);
            println!("<FORMAT_SPECIFIER>{:-}</FORMAT_SPECIFIER> <FORMAT_SPECIFIER>{:#}</FORMAT_SPECIFIER> <FORMAT_SPECIFIER>{:+#}</FORMAT_SPECIFIER>", S, S, S);
            println!("");
            println!("<FORMAT_SPECIFIER>\x7B}</FORMAT_SPECIFIER>", S);
            let mut w = Vec::new();
            write!(&mut w, "<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
            format_args!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
        }
    """)

    fun `test format trait is not implemented`() = checkErrors("""
        struct S;

        fn main() {
            let s = S;
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Display` (required by {})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Debug` (required by {:?})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>x?</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Debug` (required by {:x?})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>X?</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Debug` (required by {:X?})">s</error>);
            println!("<FORMAT_SPECIFIER>{:#<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Debug` (required by {:#?})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>o</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Octal` (required by {:o})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>x</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `LowerHex` (required by {:x})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>X</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `UpperHex` (required by {:X})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>p</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Pointer` (required by {:p})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>b</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `Binary` (required by {:b})">s</error>);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>e</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `LowerExp` (required by {:e})">s</error>);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:<FUNCTION>E</FUNCTION>}</FORMAT_SPECIFIER>", <error descr="`S` doesn't implement `UpperExp` (required by {0:E})">s</error>);
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
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>x?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>X?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:#<FUNCTION>?</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>o</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>x</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>X</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>p</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>b</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>e</FUNCTION>}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{:<FUNCTION>E</FUNCTION>}</FORMAT_SPECIFIER>", S);
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
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", *W { s: S });
        }
    """)

    fun `test format trait is implemented behind reference`() = checkErrors("""
        $implDisplayI32

        fn main() {
            let s = S;
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", &1);
        }
    """)

    fun `test match format parameters`() = checkErrors("""
        $implDisplayI32

        fn main() {
            let a = 5;
            println!("Hello <FORMAT_SPECIFIER>{:<IDENTIFIER>1${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, a);

            println!("Hello <FORMAT_SPECIFIER>{:<IDENTIFIER>1${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, 5);
            println!("Hello <FORMAT_SPECIFIER>{<IDENTIFIER>1</IDENTIFIER>:<IDENTIFIER>0${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 5, 1);
            println!("Hello <FORMAT_SPECIFIER>{<IDENTIFIER>1</IDENTIFIER>:<IDENTIFIER>0${'$'}</IDENTIFIER>.<IDENTIFIER>2${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, 1, 4);
            println!("Hello <FORMAT_SPECIFIER>{:<IDENTIFIER>width${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, width = 5);
        }
    """)

    fun `test check format parameters type`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("Hello <FORMAT_SPECIFIER>{:<IDENTIFIER>1${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, <error descr="Width specifier must be of type `usize`">"asd"</error>);
            println!("Hello <FORMAT_SPECIFIER>{:.<IDENTIFIER>1${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, <error descr="Precision specifier must be of type `usize`">2.0</error>);
            println!("Hello <FORMAT_SPECIFIER>{:<IDENTIFIER>1${'$'}</IDENTIFIER>.<IDENTIFIER>1${'$'}</IDENTIFIER>}</FORMAT_SPECIFIER>!", 1, <error descr="Precision specifier must be of type `usize`"><error descr="Width specifier must be of type `usize`">2.0</error></error>);
        }
    """)

    fun `test check precision asterisk`() = checkErrors("""
        $implDisplayI32

        fn main() {
            println!("<FORMAT_SPECIFIER>{:.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, 2);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>", 1);
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>name</IDENTIFIER>:.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, 3, name=2);
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER><FORMAT_SPECIFIER>{<IDENTIFIER>2</IDENTIFIER>:.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>", 1, 5, 2);
            println!("<FORMAT_SPECIFIER>{:<IDENTIFIER>a${'$'}</IDENTIFIER>.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>!", 5, 1, a=3);
        }
    """)

    fun `test check precision asterisk wrong parameter`() = checkErrors("""
        use std::fmt;

        struct S;
        impl fmt::Display for S {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            println!("<FORMAT_SPECIFIER>{:.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>!", <error descr="Precision specifier must be of type `usize`">"asd"</error>, S);
            println!("<FORMAT_SPECIFIER>{<IDENTIFIER>0</IDENTIFIER>:.<IDENTIFIER>*</IDENTIFIER>}</FORMAT_SPECIFIER>!", <error descr="Precision specifier must be of type `usize`">S</error>);
        }
    """)

    fun `test ignore unknown types`() = checkErrors("""
        struct G<T>(T);
        impl<T> fmt::Display for G<T> {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { unimplemented!() }
        }

        fn main() {
            let g: G<S> = G(S);
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", g);
        }
    """)

    fun `test ignore never type`() = checkErrors("""
        fn never() -> ! { unimplemented!() }

        fn main() {
            println!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", never());
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
            println!(r"\<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>!", 1);
            println!(r"\u<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>!", 1);
            println!(r##"<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>

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
}
