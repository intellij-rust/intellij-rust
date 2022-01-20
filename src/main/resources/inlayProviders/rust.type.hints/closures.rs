fn main() {
    foo(|x| x + 1);
}

fn foo(x: fn(i32) -> i32) {}
