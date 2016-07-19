fn test(pred: bool) {
    if <weak_warning>(pred)</weak_warning> {
        return <weak_warning>(true)</weak_warning>
    }
    false
}
