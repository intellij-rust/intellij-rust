use std::fs::File;

use serde::{Serialize, Serializer};
use serde::ser::SerializeSeq;

use rustc_feature::{AttributeDuplicates, AttributeGate, AttributeType, BUILTIN_ATTRIBUTES};

use crate::formatter::MyFormatter;

#[derive(Serialize)]
struct BuiltinAttributeInfo {
    name: &'static str,
    #[serde(rename = "type")]
    type_: MyAttributeType,
    template: MyAttributeTemplate,
    duplicates: &'static str,
    gated: bool,
}

#[derive(Serialize)]
pub enum MyAttributeType {
    Normal,
    CrateLevel,
}

#[derive(Serialize)]
pub struct MyAttributeTemplate {
    /// If `true`, the attribute is allowed to be a bare word like `#[test]`.
    pub word: bool,
    /// If `Some`, the attribute is allowed to take a list of items like `#[allow(..)]`.
    pub list: Option<&'static str>,
    /// If `Some`, the attribute is allowed to be a name/value pair where the
    /// value is a string, like `#[must_use = "reason"]`.
    #[serde(rename = "nameValueStr")]
    pub name_value_str: Option<&'static str>,
}

pub(crate) fn generate_builtin_attributes_json(builtin_attributes_json_path: &str) {
    let f = File::create(builtin_attributes_json_path).unwrap();
    let mut serializer = serde_json::Serializer::with_formatter(f, MyFormatter::new());

    let mut seq = serializer.serialize_seq(None).unwrap();
    let mut sorted_builtin_attributes = BUILTIN_ATTRIBUTES
        .iter()
        .collect::<Vec<_>>();
    sorted_builtin_attributes.sort_by_key(|a| a.name.as_str());
    for attr in sorted_builtin_attributes {
        let attr_info = BuiltinAttributeInfo {
            name: attr.name.as_str(),
            type_: match attr.type_ {
                AttributeType::Normal => MyAttributeType::Normal,
                AttributeType::CrateLevel => MyAttributeType::CrateLevel,
            },
            template: MyAttributeTemplate {
                word: attr.template.word,
                list: attr.template.list,
                name_value_str: attr.template.name_value_str,
            },
            duplicates: duplicates_to_str(attr.duplicates),
            gated: matches!(attr.gate, AttributeGate::Gated(..)),
        };
        seq.serialize_element(&attr_info).unwrap();
    }
    seq.end().unwrap();
}

fn duplicates_to_str(d: AttributeDuplicates) -> &'static str {
    match d {
        AttributeDuplicates::DuplicatesOk => "DuplicatesOk",
        AttributeDuplicates::WarnFollowing => "WarnFollowing",
        AttributeDuplicates::WarnFollowingWordOnly => "WarnFollowingWordOnly",
        AttributeDuplicates::ErrorFollowing => "ErrorFollowing",
        AttributeDuplicates::ErrorPreceding => "ErrorPreceding",
        AttributeDuplicates::FutureWarnFollowing => "FutureWarnFollowing",
        AttributeDuplicates::FutureWarnPreceding => "FutureWarnPreceding",
    }
}
