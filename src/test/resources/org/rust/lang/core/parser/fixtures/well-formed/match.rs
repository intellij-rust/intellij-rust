fn main() {
    match x {
        _ => {}
        _ => 1,
        _ => unsafe { 1 }.to_string(),
        _ => 92
    }
}
