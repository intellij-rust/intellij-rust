pub struct FooBar;

struct S;

trait T {
    fn foo(self, f: &mut FooBar);
}

impl T for S {
    fn foo(self, f: FooBar)
}
