trait MyTrait {
    type <info>AssocType</info>;
    fn some_fn(&self);
}

struct MyStruct<<info>N: ?Sized+Debug+MyTrait</info>> {
    N: my_field
}
