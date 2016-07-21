struct S {
    foo: i32,
    <error descr="Duplicate field 'foo: String'">foo: String</error>,
}
