struct S {
    foo: i32,
    <error descr="Duplicate field 'foo: String'">foo: String</error>,
}

enum E {
    V { foo: i32, bar: String, <error>foo: i32</error> }
}
