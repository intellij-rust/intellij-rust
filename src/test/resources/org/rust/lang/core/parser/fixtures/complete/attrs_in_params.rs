fn f1(#[attr1] #[attr2] pat: S) {}

fn f2(#[attr] x: S) {}

impl S {
    fn f3(#[attr] self) {}

    fn f4(#[attr] &self) {}

    fn f5<'a>(#[attr] &mut self) {}

    fn f6<'a>(#[attr] &'a self) {}

    fn f7<'a>(#[attr] &'a mut self, #[attr] x: S, y: S) {}

    fn f8(#[attr] self: Self) {}

    fn f9(#[attr] self: S<Self>) {}
}

trait T { fn f10(#[attr] S); }

extern "C" {
    fn f11(#[attr] x: S, #[attr] ...);
}
