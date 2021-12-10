fn main() {
    match () {
        () => {}
        () => {}
    };
    // https://github.com/intellij-rust/intellij-rust/issues/5786
    let array = [42];
    match array[..] {
        [] => {}
        [_x] => {}
        _ => {}
    };
}
