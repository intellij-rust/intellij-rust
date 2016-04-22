fn double(x: i32) -> i32 {
    double(double(x));
    <caret>
    double(x)
}
