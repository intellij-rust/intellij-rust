fn main() {
    let try = ();
    let _ = try;

    let _ = try!(());

    try { () };
}
