fn main() {
    let x: (i32, f64) = unimplemented!();
    if let (1, y<caret>) = x {

    }
}
