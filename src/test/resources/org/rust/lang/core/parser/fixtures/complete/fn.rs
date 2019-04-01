
fn add(x: i32, y: i32) -> i32 {
  return x + y;
}

fn mul(x: i32, y: i32) -> i32 {
  x * y;
}

fn id(x: i32,) -> i32 { x }

fn constant() -> i32 { 92 }

const        fn a() -> () { () }
const unsafe fn b() -> () { () }

fn diverging() -> ! { panic("! is a type") }

unsafe extern "C" fn ext_fn1(a: bool, ...) {}
unsafe extern "C" fn ext_fn2(a: bool, args: ...) {}
