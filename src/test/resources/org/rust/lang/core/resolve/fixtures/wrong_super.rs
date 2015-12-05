fn foo() {}

mod inner {
    fn main() {
        ::inner::super::f<caret>oo();
    }
}
