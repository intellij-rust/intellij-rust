fn main() {
    match x {
        _ => {}
        _ => 1,
        _ => unsafe { 1 }.to_string(),
        _ => const { 2 }.to_string(),
        _ => 92
    };

    match x {
        | 0
        | 1 => 0,
        | _ => 42,
    };

    match x {
        0 => 0,
        1 if 1 < 2 => 1,
        2 if let Some(24) | Some(42) = Some(42) => 42
    };

    match x {
        const { 0 } => 0,
        const { const { 1 } } => 1,
        const { 2 }..=const { 3 } => 2,
        _ => 3
    };
}
