extern "C" {
    fn foo();
}

fn main() {
    unsafe { <caret>foo() }
}
