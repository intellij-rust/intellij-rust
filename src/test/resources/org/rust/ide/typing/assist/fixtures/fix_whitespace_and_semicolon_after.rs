fn f(x: i32) -> i32 {
    f(f(x));
    <caret>
}
