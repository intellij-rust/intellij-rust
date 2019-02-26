fn main() {
    if 1 < 2 {}
    if let Some(x) = o {}
    if let | Err(e) = r {}
    if let V1(s) | V2(s) = value {}
    if let | Cat(name) | Dog(name) | Parrot(name) = animal {}

    while 1 < 2 {}
    while let Some(x) = o {}
    while let | Err(e) = r {}
    while let V1(s) | V2(s) = value {}
    while let | Cat(name) | Dog(name) | Parrot(name) = animal {}
}
