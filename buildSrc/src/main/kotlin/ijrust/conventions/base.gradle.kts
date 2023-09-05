package ijrust.conventions

import ijrust.IJRustBuildProperties

plugins {
    base
//    idea
}

extensions.create<IJRustBuildProperties>("ijRustBuild")
//
//idea {
//    module {
//        generatedSourceDirs.add(file("src/gen"))
//    }
//}
