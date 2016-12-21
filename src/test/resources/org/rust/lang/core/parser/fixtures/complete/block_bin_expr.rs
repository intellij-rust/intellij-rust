// See stuff around `Restrictions::RESTRICTION_STMT_EXPR` in libsyntax

pub fn foo(x: String) {
    // These are not bit and, these are two statements.
    { 1 }
    *2;

    { 1 }
    &2;

    loop {}
    *x;

    while true {}
    &1;

    loop {}
    &mut x;

    let foo = ();
    {foo}
    ();

    // These are binary expressions
    let _ = { 1 } * 2;
    let _ = { 1 } & 2;
    let _ = loop {} * 1;
    2 & { 1 };

    fn bar() {}
    let _ = {bar}();
}
