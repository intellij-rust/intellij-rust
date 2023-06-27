fn main() {
    let mut x = 1;
    match &mut x {
        &mut y => {}
    }
}
