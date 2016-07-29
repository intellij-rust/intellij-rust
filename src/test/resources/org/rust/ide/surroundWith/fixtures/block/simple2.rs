fn main() {
    let mut server = Nickel::new();<selection>
    server.get("**", hello_world);
    server.listen("127.0.0.1:6767").unwrap();</selection>
}
