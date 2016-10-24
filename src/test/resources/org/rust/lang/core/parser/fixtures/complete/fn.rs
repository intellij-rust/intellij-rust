
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
