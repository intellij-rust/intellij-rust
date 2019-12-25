fn main() {
    match x {
        _ => {}
        _ => 1,
        _ => unsafe { 1 }.to_string(),
        _ => 92
    };

    match x {
        | 0
        | 1 => 0,
        | _ => 42,
    };
}
