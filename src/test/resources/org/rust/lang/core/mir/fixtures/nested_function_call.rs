fn main() {
    let a = foo(foo(1, 2), foo(1, 2));
}

fn foo(a: i32, b: i32) -> i32 { 0 }
