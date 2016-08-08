struct S;

trait T {
    fn foo(&self);
}

impl T for  && S {
    fn foo(&self) {}
}

impl T for & S {
    fn foo(&self) {}
}

type X = S;

impl X {
    fn goo(&self) {}
}

fn main() {
    let xrr = &&S;
    let xr = &S;

    xr.<caret>foo();
    xrr.foo();
    xrr.goo(); // should be ok
}
