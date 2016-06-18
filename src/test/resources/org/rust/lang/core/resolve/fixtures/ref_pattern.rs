fn main() {
    let x = 92;
    if let Some(&y) = Some(&x) {
        <caret>y;
    }
}
