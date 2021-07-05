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
    { foo }
    ();

    // These are binary expressions
    let _ = { 1 } * 2;
    let _ = { 1 } & 2;
    let _ = loop {} * 1;
    2 & { 1 };
    x = { 0 } != 0;
    x = true && { 0 } == 0;
    loop {}
    *x = expr;

    -{ 3 } * 5;
    -{ 3 } + 5;
    -{ 3 } - 5;
    || { 3 } + 5;
    yield { 3 } + 5;
    return { 3 } + 5;

    3..{ 4 } + 5;
    3...{ 4 } + 5;
    3..={ 4 } + 5;
    x = { 3 }..4 + 5;
    x = { 3 }...4 + 5;
    x = { 3 }..=4 + 5;

    fn bar() {}
    let _ = { bar }();
}
