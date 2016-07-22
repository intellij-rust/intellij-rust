#[derive(Default)]
struct S {
    foo: i32,
    bar: i32
}

struct Empty {}

enum E {
    V {
        foo: i32
    }
}

fn main() {
    let _ = S {
        foo: 92,
        <error descr="No such field">baz</error>: 62,
        bar: 11,
    };

    let _ = S {
        foo: 92,
        ..S::default()
    };

    let _ = S {
        foo: 92,
    <error descr="Some fields are missing">}</error>;

    let _ = S {
        foo: 1,
        <error descr="Duplicate field">foo</error>: 2,
    <error descr="Some fields are missing">}</error>;

    let _ = S {
        foo: 1,
        <error>foo</error>: 2,
        ..S::default()
    };

    let _ = Empty { };

    let _ = E::V {
        <error>bar</error>: 92
    <error>}</error>;
}
