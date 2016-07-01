struct S<FOO> {
    field: FOO
}

fn main() {
    let s: S = S;

    s.<caret>transmogrify();
}

impl<BAR> S<BAR> {
    fn transmogrify(&self) { }
}

