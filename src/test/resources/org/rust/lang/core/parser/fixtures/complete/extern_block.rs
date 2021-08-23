#[link(name = "objc")]
extern {
    fn foo(name: *const libc::c_uchar);
    fn bar(a: i32,  ...) -> i32;

    #[cfg(test)]
    pub fn baz(b: i64, );

    #[doc = "Hello"]
    pub static X: i32;

    pub type Y;
}

extern "C" {}
extern "R\u{0075}st" {}
extern r"system" {}
extern 'C' {} // semantically invalid
