fn patterns() {
    let S {..} = x;
    let S {field} = x;
    let S {field,} = x;
    let S {field, ..} = x;

    let ref a @ _ = value;

    if let Some(x,) = Some(92) { }

    let m!(x) = 92;
}
