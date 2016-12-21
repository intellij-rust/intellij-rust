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

pub trait <TRAIT>Write</TRAIT> {
    fn <METHOD>write</METHOD>(&mut <SELF_PARAMETER>self</SELF_PARAMETER>, <PARAMETER>buf</PARAMETER>: &[<PRIMITIVE_TYPE>u8</PRIMITIVE_TYPE>]) -> <ENUM>Result</ENUM><usize>;
}

struct <STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>> {
    <FIELD>flag</FIELD>: <ENUM>Flag</ENUM>,
    <FIELD>fields</FIELD>: <STRUCT>HashMap</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>, <PRIMITIVE_TYPE>u64</PRIMITIVE_TYPE>>
}

type <TYPE_ALIAS>RcObject</TYPE_ALIAS><<TYPE_PARAMETER>T</TYPE_PARAMETER>> = <STRUCT>Rc</STRUCT><<STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>>>;

impl<<TYPE_PARAMETER>T</TYPE_PARAMETER>> Write for <STRUCT>Object</STRUCT><<TYPE_PARAMETER>T</TYPE_PARAMETER>> {
    fn <METHOD>write</METHOD>(&mut <SELF_PARAMETER>self</SELF_PARAMETER>, <PARAMETER>buf</PARAMETER>: &[<PRIMITIVE_TYPE>u8</PRIMITIVE_TYPE>]) -> <ENUM>Result</ENUM><usize> {
        let s = stuff::<FUNCTION>write_map</FUNCTION>(&self.<FIELD>fields</FIELD>, <PARAMETER>buf</PARAMETER>)<Q_OPERATOR>?</Q_OPERATOR>;
        <MACRO>info!</MACRO>("{} byte(s) written", s);
        <ENUM_VARIANT>Ok</ENUM_VARIANT>(s)
    }
}

/* Block comment */
fn <FUNCTION>main</FUNCTION>() {
    // A simple integer calculator:
    // `+` or `-` means add or subtract by 1
    // `*` or `/` means multiply or divide by 2
    <MODULE>stuff</MODULE>::<STRUCT>AppVersion</STRUCT>::<ASSOC_FUNCTION>print</ASSOC_FUNCTION>();

    let input = <ENUM>Option</ENUM>::<ENUM_VARIANT>None</ENUM_VARIANT>;
    let program = input.<METHOD>unwrap_or_else</METHOD>(|| "+ + * - /");
    let mut <MUT_BINDING>accumulator</MUT_BINDING> = 0;

    for token in program.<METHOD>chars</METHOD>() {
        match token {
            '+' => <MUT_BINDING>accumulator</MUT_BINDING> += 1,
            '-' => <MUT_BINDING>accumulator</MUT_BINDING> -= 1,
            '*' => <MUT_BINDING>accumulator</MUT_BINDING> *= 2,
            '/' => <MUT_BINDING>accumulator</MUT_BINDING> /= 2,
            _ => { /* ignore everything else */ }
        }
    }

    <MACRO>info!</MACRO>("The program \"{}\" calculates the value {}",
             program, <MUT_BINDING>accumulator</MUT_BINDING>);
}

/// Some documentation `with code`
/// # Heading
/// [Rust](https://www.rust-lang.org/)
<ATTRIBUTE>#[cfg(target_os=</ATTRIBUTE>"linux"<ATTRIBUTE>)]</ATTRIBUTE>
unsafe fn <FUNCTION>a_function</FUNCTION><<TYPE_PARAMETER>T</TYPE_PARAMETER>: <LIFETIME>'lifetime</LIFETIME>>() {
    'label: loop {
        <MACRO>println!</MACRO>("Hello\x20W\u{f3}rld!\u{abcdef}");
    }
}
