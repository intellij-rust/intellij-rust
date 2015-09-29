trait Contains {
    type A;
    fn inner(&self) -> Self::A;
    fn empty();
    fn anon_param(i32);
    fn self_type(x: Self, y: Vec<Self>) -> Self;
}