fn f() -> i32 {}

fn test() -> u32 {

    x :: y;         /* path-expr */

    x + y - z * 0;  /* binary */

    x = y = z;      /* assignment + ; */

    *x;             /* unary (+ ;) */
    &x;
    &mut x;
    &&& x;

    (x + y) * z;    /* parenthesized */

    t = (0, 1, 2);  /* tuple */

    t.a;            /* field */
    t.0;

    f.m();          /* method-invokation */

    f();            /* call */
    <T as Foo>::U::generic_method::<f64>();

    t = ();         /* unit */

    [   0,          /* array */
        1,
        2,
        [ 0 ; 1 ] ];
    [];
    [1,];
    [1;2];

    r = 1..2;       /* range */
    r =  ..2;
    r = 1.. ;
    r =  .. ;

    || {};          /* lambda */
    |x: i32| -> i32 92;
    move |x: i32| {
        x
    };

    { }             /* block */

    unsafe { 92 }

    // TBA(kudinkin)

    return (x = y)  /* return */
            + 1
}