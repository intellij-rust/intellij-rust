<error descr="Visibility modifier is not allowed here">pub</error> extern "C" { }

pub struct S;

<error descr="Visibility modifier is not allowed here">pub</error> impl S {}

pub trait T {}

<error descr="Visibility modifier is not allowed here">pub</error> impl T for S {}

