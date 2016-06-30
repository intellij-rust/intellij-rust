struct S;

fn main() {
    let s: S = S;

    s.<caret>transmogrify();
}

mod hidden {
    use super::S;

    impl S {
        pub fn transmogrify(self) -> S { S }
    }
}
