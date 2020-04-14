/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.vfs.AdditiveCacheLocations;

import java.io.Closeable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {

    private final PersistentCache cache;
    private final FileAccessTracker fileAccessTracker;
    private final JarCache jarCache;
    private final AdditiveCacheLocations additiveCacheLocations;

    public DefaultCachedClasspathTransformer(
        CacheRepository cacheRepository,
        ClasspathTransformerCacheFactory classpathTransformerCacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        FileHasher fileHasher,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder,
        AdditiveCacheLocations additiveCacheLocations
    ) {
        this.additiveCacheLocations = additiveCacheLocations;
        this.cache = classpathTransformerCacheFactory.createCache(cacheRepository, fileAccessTimeJournal);
        fileAccessTracker = classpathTransformerCacheFactory.createFileAccessTracker(fileAccessTimeJournal);
        jarCache = new JarCache(fileHasher, classpathWalker, classpathBuilder);
    }

    @Override
    public ClassPath transform(ClassPath classPath, Usage usage) {
        return cache.useCache(() -> {
            List<File> originalFiles = classPath.getAsFiles();
            List<File> cachedFiles = new ArrayList<>(originalFiles.size());
            for (File file : originalFiles) {
                cachedFiles.add(cached(file, usage));
            }
            return DefaultClassPath.of(cachedFiles);
        });
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls, Usage usage) {
        return cache.useCache(() -> {
            List<URL> cachedFiles = new ArrayList<>(urls.size());
            for (URL url : urls) {
                if (url.getProtocol().equals("file")) {
                    try {
                        cachedFiles.add(cached(new File(url.toURI()), usage).toURI().toURL());
                    } catch (URISyntaxException | MalformedURLException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                } else {
                    cachedFiles.add(url);
                }
            }
            return cachedFiles;
        });
    }

    private File cached(File original, Usage usage) {
        if (shouldUseFromCache(original)) {
            File result = jarCache.getCachedJar(usage, original, cache.getBaseDir());
            fileAccessTracker.markAccessed(result);
            return result;
        } else {
            return original;
        }
    }

    private boolean shouldUseFromCache(File original) {
        // Transform everything that has not already been transformed
        return !original.toPath().startsWith(cache.getBaseDir().toPath());
    }

    @Override
    public void close() {
        cache.close();
    }
}
