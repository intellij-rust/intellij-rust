type Foo = usize;

trait T {
    type O;
}

struct S;

impl T for S {
    type O = <caret>Foo;
}


