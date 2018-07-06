struct S1<A> {
    field1: A
}

struct S2<B> {
    parent: S1<B>,
    field2: B
}

impl<T> std::ops::Deref for S2<T> {
    type Target = S1<T>;

    fn deref(&self) -> &Self::Target {
        &self.parent
    }
}

fn main() {
    let s1 = S1 { field1: 1u8 };
    let s2 = S2 { parent: s1, field2: 1u8};

    s2.field1;
}
