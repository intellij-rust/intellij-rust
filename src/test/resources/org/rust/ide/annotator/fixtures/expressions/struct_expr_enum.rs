enum E {
    V {
        foo: i32
    }
}

fn main() {
    let _ = E::V {
        <error>bar: 92</error>
    <error>}</error>;
}
