fn main() {
    println!("Hello, World");
    let mut x = 5;
    let y = &x;
    x = 3;
    *y = 4;
}
