trait Spam {
    fn eggs(&self);
}

fn foo<T>(x: T) where T: Spam {
    x.<caret>eggs()
}
