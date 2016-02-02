pub fn amp_sso(request:String) -> String {
    let xml_value: Element = match from_str(&request){
        Ok(c)=>c,
        Err(_)=>return "error".to_owned()
    };
match xml_value.members {
Content::Members(m1) => {
if m1.contains_key("Assertion") {
let assertion = &m1["Assertion"][0];
match assertion.members {
Content::Members(ref m2) => {
if m2.contains_key("AttributeStatement") {
let attrs = &m2["AttributeStatement"];
for attr in attrs {
match attr.members {
Content::Members(ref m3) => {
if m3.contains_key("Attribute") {
let values = &m3["Attribute"];
if values.contains_key("AttributeValue") {
if
}
}

},
_ => ()
}
}
}
},
_ => ()
}
}
},
_ => ()
}

//  println!("{:?}", xml_value);
"see console".to_owned()
}
