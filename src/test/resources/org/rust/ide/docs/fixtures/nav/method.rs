struct S;

impl S {
    pub fn consume(self) -> i32 { 92 }
}

fn main() {
    let s = S;
    s.<caret>consume();
}
