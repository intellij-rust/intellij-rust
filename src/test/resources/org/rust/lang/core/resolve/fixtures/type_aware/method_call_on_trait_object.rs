trait T {
    fn virtual_function(&self) {}
}

fn call_virtually(obj: &T) {
    obj.<caret>virtual_function()
}
