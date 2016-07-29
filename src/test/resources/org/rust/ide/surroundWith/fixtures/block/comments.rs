fn main() {
    <selection>// comment
    let mut server = Nickel::new();
    server.get("**", hello_world);
    server.listen("127.0.0.1:6767").unwrap(); // comment</selection>
}
