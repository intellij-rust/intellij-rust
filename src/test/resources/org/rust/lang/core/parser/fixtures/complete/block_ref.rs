pub fn foo(x: String) {
    // These are not bit and, these are two statements.
    { 1 } & 2;
    while true {} & 1;
    loop {}
        & mut x;
}
