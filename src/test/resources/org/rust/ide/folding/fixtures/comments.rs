<fold text='/* ... */'>/*
hello
*/</fold>

<fold text='/* ... */'>///
/// Outer doc
///</fold>
fn foo() <fold text='{...}'>{}</fold>

mod m <fold text='{...}'>{
<fold text='/* ... */'>//!
    //! Inner doc
    //!</fold>
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
