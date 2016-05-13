fn test() {
    if <weak_warning descr="Predicate expression has unnecessary parentheses">(true)</weak_warning> {
        let _ = 1;
    }
}
