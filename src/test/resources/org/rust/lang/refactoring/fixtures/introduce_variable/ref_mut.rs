fn read_fle() -> Result<String, Error> {
    let file = File::open("res/input.txt")?;

    file.read_to_string(&mut String:<caret>:new())?;

    Ok(x)
}
