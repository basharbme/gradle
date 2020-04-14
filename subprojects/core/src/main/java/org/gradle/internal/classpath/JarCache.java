/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.classpath;

import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.GFileUtils;

import java.io.File;

public class JarCache {
    private final FileHasher fileHasher;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;

    public JarCache(FileHasher fileHasher, ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder) {
        this.fileHasher = fileHasher;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
    }

    /**
     * Returns a cached copy of the given file. The cached copy is guaranteed to not be modified or removed.
     */
    public File getCachedJar(CachedClasspathTransformer.Usage usage, File original, File cacheDir) {
        HashCode hashValue = fileHasher.hash(original);
        File cachedFile;
        if (usage == CachedClasspathTransformer.Usage.BuildLogic) {
            cachedFile = new File(cacheDir, hashValue.toString() + '/' + original.getName());
            if (!cachedFile.isFile()) {
                // Unpack and rebuild the jar. Later, this will apply some transformations to the classes
                classpathBuilder.jar(cachedFile, builder -> classpathWalker.visit(original, entry -> {
                    builder.put(entry.getName(), entry.getContent());
                }));
            }
        } else if (usage == CachedClasspathTransformer.Usage.Other) {
            cachedFile = new File(cacheDir, "o_" + hashValue.toString() + '/' + original.getName());
            if (!cachedFile.isFile()) {
                // Just copy the jar
                GFileUtils.copyFile(original, cachedFile);
            }
        } else {
            throw new IllegalArgumentException(String.format("Unknown usage %s", usage));
        }
        return cachedFile;
    }
}
