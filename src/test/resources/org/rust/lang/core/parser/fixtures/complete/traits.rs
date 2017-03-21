trait Contains {
    type A;
    fn inner(&self) -> Self::A;
    fn empty();
    fn anon_param(i32);
    fn self_type(x: Self, y: Vec<Self>) -> Self;
}

fn foo() {
    trait Inner {};
    unsafe trait UnsafeInner {};
}

trait bar<T> {
    fn baz(&self,);
}

trait TrailingPlusIsOk: Clone+{}
trait EmptyBoundsAreValid: {}
