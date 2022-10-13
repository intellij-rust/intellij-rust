fn main() {
    if 1 < 2 {}
    if let Some(x) = o {}
    if let | Err(e) = r {}
    if let V1(s) | V2(s) = value {}
    if let | Cat(name) | Dog(name) | Parrot(name) = animal {}
    if let Ok(V1(s) | V2(s)) = value {}
    if let V1(s) = value && let V2(s) = x {}
    if let V1(s) = value && 1 < 2 = x {}
    if 1 < 2 && let V2(s) = value = x {}

    if let x = (true && false) && let y = (false && true) {}

    while 1 < 2 {}
    while let Some(x) = o {}
    while let | Err(e) = r {}
    while let V1(s) | V2(s) = value {}
    while let | Cat(name) | Dog(name) | Parrot(name) = animal {}
    while let Ok(V1(s) | V2(s)) = value {}
    while let V1(s) = value && let V2(s) = x {}
    while let V1(s) = value && 1 < 2 = x {}
    while 1 < 2 && let V2(s) = value = x {}
}
