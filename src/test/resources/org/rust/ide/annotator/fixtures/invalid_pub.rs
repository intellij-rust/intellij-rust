<error descr="Visibility modifier is not allowed here">pub</error> extern "C" { }

pub struct S;

<error descr="Visibility modifier is not allowed here">pub</error> impl S {}

pub trait T {
    fn foo() {}
}

<error descr="Visibility modifier is not allowed here">pub</error> impl T for S {
    <error descr="Visibility modifier is not allowed here">pub</error> fn foo() {}
}

impl S {
    pub fn foo() {}
}


