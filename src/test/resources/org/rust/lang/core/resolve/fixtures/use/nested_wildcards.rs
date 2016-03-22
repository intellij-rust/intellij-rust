mod a {
    pub fn foo(){}
}

mod b {
    pub use a::*;
}

mod c {
    use b::*;

    fn main() {
        <caret>foo()
    }
}
