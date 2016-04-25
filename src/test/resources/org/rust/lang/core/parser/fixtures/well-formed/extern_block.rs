#[link(name = "objc")]
extern {
    fn foo(name: *const libc::c_uchar);
    fn bar(a: i32,  ...) -> i32;
    fn baz(b: i64, );
}
