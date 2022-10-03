use std::fs::File;
use std::io;
use std::io::Write;

use serde::{Deserialize, Serialize, Serializer};
use serde::ser::SerializeSeq;
use serde_json::ser::{Formatter, PrettyFormatter};

use rustc_feature::{ACCEPTED_FEATURES, ACTIVE_FEATURES, Feature, Features, REMOVED_FEATURES, STABLE_REMOVED_FEATURES, State};

#[derive(Serialize, Deserialize)]
struct FeatureInfo {
    name: String,
    state: String,
    since: String
}

impl FeatureInfo {
    fn from(f: &Feature) -> Self {
        FeatureInfo {
            name: f.name.as_str().to_string(),
            state: state_str(f),
            since: f.since.to_string()
        }
    }
}

struct MyFormatter {
    pretty_formatter: PrettyFormatter<'static>
}

impl MyFormatter {
    fn new() -> Self {
        MyFormatter { pretty_formatter: PrettyFormatter::new() }
    }
}

impl Formatter for MyFormatter {
    fn begin_array<W: ?Sized + Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty_formatter.begin_array(writer)
    }

    fn end_array<W: ?Sized + Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty_formatter.end_array(writer)
    }

    fn begin_array_value<W: ?Sized + Write>(&mut self, writer: &mut W, first: bool) -> io::Result<()> {
        self.pretty_formatter.begin_array_value(writer, first)
    }

    fn end_array_value<W: ?Sized + Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty_formatter.end_array_value(writer)
    }
}

fn main() {
    let f = File::create("src/main/resources/compiler-info/compiler-features.json").unwrap();
    let mut serializer = serde_json::Serializer::with_formatter(f, MyFormatter::new());

    let mut seq = serializer.serialize_seq(None).unwrap();
    [ACCEPTED_FEATURES, ACTIVE_FEATURES, REMOVED_FEATURES, STABLE_REMOVED_FEATURES]
        .iter()
        .flat_map(|features| features.iter().map(FeatureInfo::from))
        .for_each(|f| seq.serialize_element(&f).unwrap());
    seq.end().unwrap();
}

fn state_str(feature: &Feature) -> String {
    match feature.state {
        State::Accepted => "accepted",
        State::Active { .. } => {
            if Features::default().incomplete(feature.name) {
                "incomplete"
            } else {
                "active"
            }
        },
        State::Removed { .. } => "removed",
        State::Stabilized { .. } => "stabilized"
    }.to_string()
}
