package org.rust.cargo.project.model

class CargoTarget(val name: String, val kind: List<String>, val src_path: String, val metadata: Map<String, String>)