mod inner {
    pub mod child;
}

fn main() {
    inner::child::<caret>foo();
}
