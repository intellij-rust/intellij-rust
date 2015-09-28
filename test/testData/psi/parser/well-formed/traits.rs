trait Contains {
    type A;
    fn inner(&self) -> Self::A;
    fn empty();
    fn anon_param(i32);
}