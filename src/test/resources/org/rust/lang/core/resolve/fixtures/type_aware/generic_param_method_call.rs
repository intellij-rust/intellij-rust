trait Spam {
    fn eggs(&self);
}

fn foo<T: Spam>(x: T) {
    x.<caret>eggs()
}
