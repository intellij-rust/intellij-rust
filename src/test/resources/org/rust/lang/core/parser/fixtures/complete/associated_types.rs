trait T {
    type B;
    type A = Self;
}

struct S;

impl T for S {
    type B = T;
}
