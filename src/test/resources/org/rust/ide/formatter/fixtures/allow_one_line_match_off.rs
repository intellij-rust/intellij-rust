fn main() {
    let x = match func() { Ok(v) => v.unwrap_or(0), Err(_) => ()};
}
