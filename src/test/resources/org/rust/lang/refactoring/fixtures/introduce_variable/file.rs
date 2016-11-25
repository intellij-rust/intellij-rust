fn read_fle() -> Result<Vec<String, io::Error>> {
    File::op<caret>en("res/input.txt")?
}
