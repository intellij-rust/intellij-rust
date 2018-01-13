fn main(param: i32) {}

pub use bar::nested::hello;

extern {
    #[cfg(any(a, all(not(b), c)))]
    fn ext1(val: u32) -> u64;
    static ext2: u8;
}

#[test]
fn bar() {}

/// Doc
/// comment
pub struct S {
    pub f: f32,
    g: u32
}

enum E {
    Ok,
    Error((u32, u8))
}

#[repr(C)]
union U {
    f1: u32,
    f2: f32,
}

trait T {
    fn foo(self);
    fn bar(self) {}
    fn new() -> Self;
    const C1: u32;
    const C2: u32 = 1;
    type T1;
    type T2 = (u8, i8);
}

impl T for S {
    fn foo(self) {}
    const C1: u32 = 2;
    const C2: u32 = 3;
    type T1 = u8;
    type T2 = u16;
}

pub mod bar;

macro_rules! if_std {
    ($($i:item)*) => ($(
        #[cfg(feature = "use_std")]
        $i
    )*)
}

if_std! {
    use bar::foo as f;
}

#[path="quux.rs"]
pub mod baz;
