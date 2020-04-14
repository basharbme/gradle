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

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.util.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;

public class JarCache {
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final VirtualFileSystem virtualFileSystem;

    public JarCache(VirtualFileSystem virtualFileSystem, ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder) {
        this.virtualFileSystem = virtualFileSystem;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
    }

    /**
     * Returns a cached copy of the given file. The cached copy is guaranteed to not be modified or removed.
     */
    @Nullable
    public File getCachedJar(CachedClasspathTransformer.Usage usage, File original, File cacheDir) {
        HashCode hashValue = virtualFileSystem.read(original.getAbsolutePath(), CompleteFileSystemLocationSnapshot::getHash);
        if (usage == CachedClasspathTransformer.Usage.BuildLogic) {
            File transformed = new File(cacheDir, hashValue.toString() + '/' + original.getName());
            if (!transformed.isFile()) {
                // Unpack and rebuild the jar. Later, this will apply some transformations to the classes
                classpathBuilder.jar(transformed, builder -> classpathWalker.visit(original, entry -> {
                    builder.put(entry.getName(), entry.getContent());
                }));
            }
            return transformed;
        } else if (usage == CachedClasspathTransformer.Usage.Other) {
            // TODO - reuse information from the VFS read above
            if (!original.isFile()) {
                return original;
            }
            File cachedFile = new File(cacheDir, "o_" + hashValue.toString() + '/' + original.getName());
            if (!cachedFile.isFile()) {
                // Just copy the jar
                GFileUtils.copyFile(original, cachedFile);
            }
            return cachedFile;
        } else {
            throw new IllegalArgumentException(String.format("Unknown usage %s", usage));
        }
    }
}
