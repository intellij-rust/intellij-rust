// Taken from issue #185

fn read_manifest_output() -> String {
    "\
{\
    \"name\":\"foo\",\
    \"version\":\"0.5.0\",\
    \"dependencies\":[],\
    \"targets\":[{\
        \"kind\":[\"bin\"],\
        \"name\":\"foo\",\
        \"src_path\":\"src[..]foo.rs\"\
    }],\<caret>
    \"manifest_path\":\"[..]Cargo.toml\"\
}".into()
}
