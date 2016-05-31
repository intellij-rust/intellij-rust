mod foo {
    pub struct Bar;
}

fn main() {
    use foo::Bar;

    let _ = <caret>Bar;
}
