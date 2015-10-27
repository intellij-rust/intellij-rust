fn main() {
    match Some(92) {
        Some(i) => { <caret>i }
        _ => 0
    };
}
