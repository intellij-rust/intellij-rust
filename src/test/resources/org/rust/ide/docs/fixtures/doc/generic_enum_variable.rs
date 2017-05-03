enum E<T> {
    L,
    R(T),
}

fn main() {
    let x = E::R(123);
    x<caret>;
}
