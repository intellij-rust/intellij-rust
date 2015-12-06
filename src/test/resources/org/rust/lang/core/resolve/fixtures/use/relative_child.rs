mod a {
    use self::b::f<caret>oo;

    mod b {
        pub fn foo() {}
    }
}
