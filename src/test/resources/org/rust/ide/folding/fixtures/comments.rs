<fold text='/* ... */'>/*
hello
*/</fold>

<fold text='/* ... */'>///
/// Outer eol doc
///</fold>
fn foo() <fold text='{...}'>{}</fold>

mod m <fold text='{...}'>{
<fold text='/* ... */'>//!
//! Inner eol doc
    //!</fold>
}</fold>

<fold text='/* ... */'>/**
    Outer block doc
*/</fold>
fn foo() <fold text='{...}'>{}</fold>

mod m <fold text='{...}'>{
<fold text='/* ... */'>/*!
    Inner block doc
*/</fold>
}</fold>

<fold text='/* ... */'>/*
hello
*/</fold>

<fold text='/* ... */'>/*
fn foo() {}
*/</fold>

mod m <fold text='{...}'>{
<fold text='/* ... */'>/*
    */</fold>
}</fold>
