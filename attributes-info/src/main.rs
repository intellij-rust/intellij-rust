use std::fs::File;
use std::io::Write;
use rustc_feature::{ACCEPTED_FEATURES, ACTIVE_FEATURES, Feature, Features, REMOVED_FEATURES, STABLE_REMOVED_FEATURES, State};

fn main() {
    let mut f = File::create("src/main/kotlin/org/rust/lang/core/CompilerFeatures.kt").unwrap();

    f.write_all(br#"/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.lang.core

import org.rust.lang.core.FeatureState.*
"#).unwrap();

    write_features(&mut f, ACCEPTED_FEATURES, "Accepted features");
    write_features(&mut f, ACTIVE_FEATURES, "Active features");
    write_features(&mut f, REMOVED_FEATURES, "Removed features");
    write_features(&mut f, STABLE_REMOVED_FEATURES, "Stable removed features");
}

fn write_features(f: &mut File, features: &[Feature], comment: &str) {
    writeln!(f, "\n// {}", comment).unwrap();
    for feature in features {
        let feature_name = feature.name.as_str();
        writeln!(f, "val {} = CompilerFeature(\"{feature_name}\", {}, \"{}\")",
                 feature_name.to_uppercase(),
                 to_kotlin_state(feature),
                 feature.since,
        ).unwrap();
    }
}

fn to_kotlin_state(feature: &Feature) -> &'static str {
    match feature.state {
        State::Accepted => "ACCEPTED",
        State::Active { .. } => {
            if Features::default().incomplete(feature.name) {
                "INCOMPLETE"
            } else {
                "ACTIVE"
            }
        },
        State::Removed { .. } => "REMOVED",
        State::Stabilized { .. } => "STABILIZED"
    }
}
