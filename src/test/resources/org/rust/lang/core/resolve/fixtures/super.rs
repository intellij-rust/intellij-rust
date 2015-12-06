fn foo() {}

mod inner {
    fn main() {
        super::f<caret>oo();
    }
}
