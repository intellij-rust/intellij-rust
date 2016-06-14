mod foo {
    fn transmogrify() {}
}

fn main() {
    use foo::*;

    transmogrify
}
