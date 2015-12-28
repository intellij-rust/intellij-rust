/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rust.jps;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RustBuilder extends ModuleLevelBuilder {

    public RustBuilder() {
        super(BuilderCategory.TRANSLATOR);
    }


    public ExitCode build(final CompileContext context,
                          final ModuleChunk chunk,
                          final DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          final OutputConsumer outputConsumer) throws ProjectBuildException, IOException {

        context.processMessage(new CompilerMessage("rust", BuildMessage.Kind.INFO, "Start"));

        for (JpsModule module : chunk.getModules()) {
            String path = getContentRootPath(module);

            ProcessBuilder processBuilder = new ProcessBuilder("cargo", "build");
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(new File(path));
            final Process process = processBuilder.start();
            processOut(context, process);
            try {
                if (process.waitFor() != 0) {
                    return ExitCode.ABORT;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }



        return ExitCode.OK;
    }

    private void processOut(CompileContext context, Process process) throws IOException {
        Iterator<String> processOut = collectOutput(process);

        while (processOut.hasNext()) {
            String line = processOut.next();
            context.processMessage(new CompilerMessage("rust", BuildMessage.Kind.INFO, line));
        }
    }

    private Iterator<String> collectOutput(Process process) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        return new Iterator<String>() {

            String line = null;

            @Override
            public boolean hasNext() {
                return fetch() != null;
            }

            private String fetch() {
                if (line == null) {
                    try {
                        line = reader.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return line;
            }

            @Override
            public String next() {
                String result = fetch();
                line = null;
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private String getContentRootPath(JpsModule module) {
        final List<String> urls = module.getContentRootsList().getUrls();
        if (urls.size() == 0) {
            throw new RuntimeException("Can't find content root in module");
        }
        String url = urls.get(0);
        return url.substring("file://".length());
    }


    @Override
    public List<String> getCompilableFileExtensions() {
        return Collections.singletonList("rs");
    }


    @Override
    public String toString() {
        return getPresentableName();
    }

    @NotNull
    public String getPresentableName() {
        return "Cargo builder";
    }

}
