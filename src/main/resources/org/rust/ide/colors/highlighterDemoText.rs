<ATTRIBUTE>#[macro_use]</ATTRIBUTE>
extern crate <CRATE>log</CRATE>;

use std::collections::<STRUCT>HashMap</STRUCT>;
use std::rc::<STRUCT>Rc</STRUCT>;

mod <MODULE>stuff</MODULE>;

pub enum <ENUM>Flag</ENUM> {
    <ENUM_VARIANT>Good</ENUM_VARIANT>,
    <ENUM_VARIANT>Bad</ENUM_VARIANT>,
    <ENUM_VARIANT>Ugly</ENUM_VARIANT>
}

const <CONSTANT>QUALITY</CONSTANT>: <ENUM>Flag</ENUM> = <ENUM>Flag</ENUM>::<ENUM_VARIANT>Good</ENUM_VARIANT>;

static <STATIC>COUNTER</STATIC>: <STRUCT>AtomicUsize</STRUCT> = <STRUCT>AtomicUsize</STRUCT>::<ASSOC_FUNCTION_CALL>new</ASSOC_FUNCTION_CALL>(0);

extern "C" {
    static mut <MUT_STATIC>ERROR_MESSAGE</MUT_STATIC>: *mut std::os::raw::c_char;
}

struct Table<const <CONST_PARAMETER>N</CONST_PARAMETER>: usize>([[i32; <CONST_PARAMETER>N</CONST_PARAMETER>]; <CONST_PARAMETER>N</CONST_PARAMETER>])

pub trait <TRAIT>Write</TRAIT> {
    fn <METHOD>write</METHOD>(&mut <SELF_PARAMETER>self</SELF_PARAMETER>, <PARAMETER>buf</PARAMETER>: &[<PRIMITIVE_TYPE>u8</PRIMITIVE_TYPE>]) -> <ENUM>Result</ENUM><usize>;
}

struct <STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>> {
    <FIELD>flag</FIELD>: <ENUM>Flag</ENUM>,
    <FIELD>fields</FIELD>: <STRUCT>HashMap</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>, <PRIMITIVE_TYPE>u64</PRIMITIVE_TYPE>>
}

<KEYWORD>union</KEYWORD> <UNION>MyUnion</UNION> {
    <FIELD>f1</FIELD>: <PRIMITIVE_TYPE>u32</PRIMITIVE_TYPE>,
    <FIELD>f2</FIELD>: <PRIMITIVE_TYPE>f32</PRIMITIVE_TYPE>,
}

type <TYPE_ALIAS>RcObject</TYPE_ALIAS><<TYPE_PARAMETER>T</TYPE_PARAMETER>> = <STRUCT>Rc</STRUCT><<STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>>>;

impl<<TYPE_PARAMETER>T</TYPE_PARAMETER>> Write for <STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>> {
    fn <METHOD>write</METHOD>(&mut <SELF_PARAMETER>self</SELF_PARAMETER>, <PARAMETER>buf</PARAMETER>: &[<PRIMITIVE_TYPE>u8</PRIMITIVE_TYPE>]) -> <ENUM>Result</ENUM><usize> {
        let <VARIABLE>s</VARIABLE> = stuff::<FUNCTION_CALL>write_map</FUNCTION_CALL>(&self.<FIELD>fields</FIELD>, <PARAMETER>buf</PARAMETER>)<Q_OPERATOR>?</Q_OPERATOR>;
        <MACRO>info!</MACRO>("{} byte(s) written", <VARIABLE>s</VARIABLE>);
        <ENUM_VARIANT>Ok</ENUM_VARIANT>(<VARIABLE>s</VARIABLE>)
    }
}

impl<<TYPE_PARAMETER>T</TYPE_PARAMETER>> <TRAIT>Default</TRAIT> for <STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>> {
    fn <ASSOC_FUNCTION>default</ASSOC_FUNCTION>() -> Self {
        <STRUCT>Object</STRUCT> { <FIELD>flag</FIELD>: <ENUM>Flag</ENUM>::<ENUM_VARIANT>Good</ENUM_VARIANT>, <FIELD>fields</FIELD>: <STRUCT>HashMap</STRUCT>::<ASSOC_FUNCTION_CALL>new</ASSOC_FUNCTION_CALL>() }
    }
}

<MACRO>macro_rules</MACRO><MACRO>!</MACRO> make_wrapper {
    (<MACRO>$</MACRO><FUNCTION>wrapper_ty</FUNCTION><MACRO>:</MACRO><MACRO>ident</MACRO>, <MACRO>$</MACRO><FUNCTION>base_ty</FUNCTION><MACRO>:</MACRO><MACRO>ty</MACRO> <MACRO>$</MACRO><MACRO>(</MACRO>, <MACRO>$</MACRO><FUNCTION>lu_ty</FUNCTION><MACRO>:</MACRO><MACRO>ty</MACRO><MACRO>)</MACRO><MACRO>?</MACRO>) => {
        pub struct <MACRO>$</MACRO><FUNCTION>wrapper_ty</FUNCTION>(<MACRO>$</MACRO><FUNCTION>base_ty</FUNCTION>);
        impl From<<MACRO>$</MACRO><FUNCTION>base_ty</FUNCTION>> for <MACRO>$</MACRO><FUNCTION>wrapper_ty</FUNCTION> {
            fn from(base: <MACRO>$</MACRO><FUNCTION>base_ty</FUNCTION>) -> Self {
                Self(base)
            }
        }
        <MACRO>$</MACRO><MACRO>(</MACRO>
            impl From<<MACRO>$</MACRO><FUNCTION>lu_ty</FUNCTION>> for <MACRO>$</MACRO><FUNCTION>wrapper_ty</FUNCTION> {
                fn from(lu: <MACRO>$</MACRO><FUNCTION>lu_ty</FUNCTION>) -> Self {
                    Self(lu.get())
                }
            }
            impl From<<MACRO>$</MACRO><FUNCTION>wrapper_ty</FUNCTION>> for <MACRO>$</MACRO><FUNCTION>lu_ty</FUNCTION> {
                fn from(st: <MACRO>$</MACRO><FUNCTION>wrapper_ty</FUNCTION>) -> Self {
                    Self::new(st.0)
                }
            }
        <MACRO>)</MACRO><MACRO>?</MACRO>
    }
}

/* Block comment */
fn <FUNCTION>main</FUNCTION>() {
    // A simple integer calculator:
    // `+` or `-` means add or subtract by 1
    // `*` or `/` means multiply or divide by 2
    <MODULE>stuff</MODULE>::<STRUCT>AppVersion</STRUCT>::<ASSOC_FUNCTION_CALL>print</ASSOC_FUNCTION_CALL>();

    let <VARIABLE>input</VARIABLE> = <ENUM>Option</ENUM>::<ENUM_VARIANT>None</ENUM_VARIANT>;
    let <VARIABLE>program</VARIABLE> = <VARIABLE>input</VARIABLE>.<METHOD_CALL>unwrap_or_else</METHOD_CALL>(|| "+ + * - /");
    let mut <MUT_BINDING><VARIABLE>accumulator</VARIABLE></MUT_BINDING> = 0;

    for <VARIABLE>token</VARIABLE> in <VARIABLE>program</VARIABLE>.<METHOD_CALL>chars</METHOD_CALL>() {
        match <VARIABLE>token</VARIABLE> {
            '+' => <MUT_BINDING><VARIABLE>accumulator</VARIABLE></MUT_BINDING> += 1,
            '-' => <MUT_BINDING><VARIABLE>accumulator</VARIABLE></MUT_BINDING> -= 1,
            '*' => <MUT_BINDING><VARIABLE>accumulator</VARIABLE></MUT_BINDING> *= 2,
            '/' => <MUT_BINDING><VARIABLE>accumulator</VARIABLE></MUT_BINDING> /= 2,
            _ => { /* ignore everything else */ }
        }
    }

    <MACRO>info!</MACRO>("The program \"{}\" calculates the value {}",
            <VARIABLE>program</VARIABLE>, <MUT_BINDING><VARIABLE>accumulator</VARIABLE></MUT_BINDING>);
}

<DOC_COMMENT>/// Some documentation <DOC_CODE>`with a code`</DOC_CODE>, <DOC_EMPHASIS>*an italic text*</DOC_EMPHASIS>
/// and <DOC_STRONG>**a bold text**</DOC_STRONG>
/// <DOC_HEADING># Heading</DOC_HEADING>
/// <DOC_LINK>[Rust](https://www.rust-lang.org/)</DOC_LINK></DOC_COMMENT>
<ATTRIBUTE>#[cfg(target_os=</ATTRIBUTE>"linux"<ATTRIBUTE>)]</ATTRIBUTE>
<KEYWORD_UNSAFE>unsafe</KEYWORD_UNSAFE> fn <FUNCTION>a_function</FUNCTION><<TYPE_PARAMETER>T</TYPE_PARAMETER>: <LIFETIME>'lifetime</LIFETIME>>(<MUT_PARAMETER>count</MUT_PARAMETER>: &mut i64) -> ! {
    <MUT_PARAMETER>count</MUT_PARAMETER> += 1;
    'label: loop {
        let <VARIABLE>str_with_escapes</VARIABLE> = "Hello\x20W\u{f3}rld!\u{abcdef}";
        <MACRO>println!</MACRO>("<FORMAT_PARAMETER>{}</FORMAT_PARAMETER> <FORMAT_PARAMETER>{<FORMAT_SPECIFIER>foo</FORMAT_SPECIFIER>:<<FORMAT_SPECIFIER>4</FORMAT_SPECIFIER>}</FORMAT_PARAMETER>", str_with_escapes, foo = 42);
    }
}

fn <FUNCTION>test</FUNCTION>() {
    <KEYWORD_UNSAFE>unsafe</KEYWORD_UNSAFE> {
        <UNSAFE_CODE>a_function</UNSAFE_CODE>(1);
    }
}

<CFG_DISABLED_CODE>#[cfg(feature = "disabled_feature")]
fn cfg_disabled_function() {}</CFG_DISABLED_CODE>
