extern "C" {
    static FOO: i32;
}

fn main() {
    let _ = <caret>FOO;
}
