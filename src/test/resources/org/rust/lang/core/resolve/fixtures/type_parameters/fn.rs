fn foo<T>() -> T {
    let x: <caret>T = unimplemented!();
    x
}
