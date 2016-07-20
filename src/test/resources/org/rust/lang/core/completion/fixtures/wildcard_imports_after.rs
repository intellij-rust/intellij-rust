mod foo {
    fn transmogrify() {}
}

fn main() {
    use self::foo::*;

    transmogrify
}
