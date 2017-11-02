struct S;

trait A {
    type B;
}

impl A for S {
    type B = S;
}


trait T { }
trait P<X> { }


impl T  { }
impl (T) { }
impl T for S { }
// Syntactically invalid
//impl (T) for S { }

impl<U> P<U> { }
impl<U> (P<U>) { }
impl<U> P<U> for S { }
impl T for <S as A>::B { }

// Semantically invalid
impl (<S as A>::B) { }

impl<'a, T> Iterator for Iter<'a, T> + 'a {
    type Item = &'a T;

    foo!();
}

impl<T> GenVal<T> {
    fn value(&self) -> &T {}
    fn foo<A, B>(&mut self, a: i32, b: i32) -> &A {}
}

impl<T: fmt::Display + ?Sized> ToString for T {
    #[inline]
    default fn to_string(&self) -> String { }
    default fn a() {}
    default fn b() {}
    default const BAR: u32 = 81;
}

default unsafe impl X for X {}
