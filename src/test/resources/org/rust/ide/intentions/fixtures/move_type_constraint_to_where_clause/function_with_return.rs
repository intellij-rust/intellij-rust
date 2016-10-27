fn foo<T: Send,<caret> F: Sync>(t: T, f: F) -> i32 {
    0
}
