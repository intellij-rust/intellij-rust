fn read_fle() -> Result<String, Error> {
    let file = File::open("res/input.txt")?;
    let mut x = String::new();

    file.read_to_string(&mut x)?;

    Ok(x)
}
