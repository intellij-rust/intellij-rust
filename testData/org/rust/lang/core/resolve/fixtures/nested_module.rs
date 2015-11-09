mod a {
    mod b {
        mod c {
            pub fn foo() { }
        }
    }

    fn main() {
        b::c::f<caret>oo();
    }
}
