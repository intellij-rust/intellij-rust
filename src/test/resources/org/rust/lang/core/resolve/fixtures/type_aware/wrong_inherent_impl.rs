struct S;

fn main() {
    let s: S = S;

    s.<caret>transmogrify();
}

mod hidden {
    struct S;

    impl S {
        pub fn transmogrify(self) -> S { S }
    }
}
