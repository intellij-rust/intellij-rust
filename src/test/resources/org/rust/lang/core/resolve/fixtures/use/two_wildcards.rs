mod a {
    pub fn foo() {}
}

mod b {
    pub fn bar() {}
}

mod c {
    use a::*;
    use b::*;

    fn main() {
        <caret>bar()
    }
}
