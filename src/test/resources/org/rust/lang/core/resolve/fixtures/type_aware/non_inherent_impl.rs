struct S;

mod m {
    trait T {
        fn foo();
    }
}

mod hidden {
    use super::S;
    use super::m::T;

    impl T for S {
        fn foo() {}
    }
}

fn main() {
    use m::T;

    let _ = S::<caret>foo();
}
