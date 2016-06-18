fn f(i: i32) -> Option<i32> {}

fn bar() {
    if let Some(x) = f() {
        if let Some(y) = f(<caret>x) {
            if let Some(z) = f(y) {}
        }
    }
}
