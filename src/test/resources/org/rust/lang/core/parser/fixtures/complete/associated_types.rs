trait T {
    type A;
    type B = Self;
    type C<U>;
    type D<U> = E<U>;
}

struct S;

impl T for S {
    type A = T;
    type C<U> = T<U>;
}
