// https://github.com/rust-lang/rust/issues/31436

fn main() {
    "1".parse::<i32>()?;
    {x}?;
    x[y?]?;
    x???;
    Ok(true);
    let question_should_bind_tighter = !x?;
}
