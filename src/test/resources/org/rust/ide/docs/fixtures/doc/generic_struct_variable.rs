struct S<T> { s: T }

fn main() {
    let x = S { s: 123 };
    x<caret>;
}
