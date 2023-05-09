fn foo() {
    let mut a = 43;
    let b = &a;
    let c = *b;
    let d = &mut a;
    *d = 42;
}
