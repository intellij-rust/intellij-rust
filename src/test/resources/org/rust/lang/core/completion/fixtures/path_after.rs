mod foo {
    mod bar {
        fn frobnicate() {}
    }
}

fn frobfrobfrob() {}

fn main() {
    foo::bar::frobnicate
}
