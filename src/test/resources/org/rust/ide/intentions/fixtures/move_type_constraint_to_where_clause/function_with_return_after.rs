fn foo<T, F>(t: T, f: F) -> i32 where T: Send, F: Sync<caret> {
    0
}
