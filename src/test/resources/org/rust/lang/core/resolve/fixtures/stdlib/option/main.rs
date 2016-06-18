fn f(i: i32) -> Option<i32> {}

fn bar() {
    if let Some(x) = f() {
        if let S<caret>ome(y) = f(x) {
            if let Some(z) = f(y) {}
        }
    }
}
