fn main() {
    let version_req = match version {
        Some(v) => try!(VersionReq::parse(v)),
        None => VersionReq::any()
    }<caret>
}
