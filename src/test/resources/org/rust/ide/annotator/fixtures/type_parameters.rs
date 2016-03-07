trait MyTrait {
    type <info>AssocType</info>;
    fn <info>some_fn</info>(&self);
}

struct MyStruct<<info>N: ?Sized+Debug+MyTrait</info>> {
    N: my_field
}
