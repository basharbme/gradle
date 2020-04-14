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

package org.gradle.internal.classpath

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.UsedGradleVersions
import org.gradle.internal.Factory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static org.gradle.internal.classpath.CachedClasspathTransformer.Usage.BuildLogic
import static org.gradle.internal.classpath.CachedClasspathTransformer.Usage.Other

class DefaultCachedClasspathTransformerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def cachedDir = testDir.file("cached")
    def otherStore = testDir.file("other-store").createDir()
    def cache = Stub(PersistentCache) {
        getBaseDir() >> cachedDir
        useCache(_) >> { Factory f -> f.create() }
    }
    def cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withCrossVersionCache(_) >> { cacheBuilder }
        withLockOptions(_) >> { cacheBuilder }
        withCleanup(_) >> { cacheBuilder }
    }
    def cacheScopeMapping = Stub(CacheScopeMapping) {
        getBaseDirectory(_, _, _) >> cachedDir
    }
    def cacheRepository = Stub(CacheRepository) {
        cache(_) >> cacheBuilder
    }
    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    def usedGradleVersions = Stub(UsedGradleVersions)

    def cacheFactory = new DefaultClasspathTransformerCacheFactory(cacheScopeMapping, usedGradleVersions)
    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    def classpathBuilder = new ClasspathBuilder()
    def virtualFileSystem = TestFiles.virtualFileSystem()

    @Subject
    DefaultCachedClasspathTransformer transformer = new DefaultCachedClasspathTransformer(cacheRepository, cacheFactory, fileAccessTimeJournal, classpathWalker, classpathBuilder, virtualFileSystem)

    def "skips missing file when usage is unknown"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        expect:
        def cachedClasspath = transformer.transform(classpath, Other)
        cachedClasspath.empty
    }

    def "skips missing file when usage is for build logic"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        expect:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)
        cachedClasspath.empty
    }

    def "copies file to cache when usage is unknown"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)

        expect:
        def cachedClasspath = transformer.transform(classpath, Other)
        cachedClasspath.asFiles == [testDir.file("cached/o_f5a09326b59a1858b25c66c1fbb64d66/thing.jar")]
    }

    @Ignore
    def "reuses file from cache when usage is unknown"() {
        expect: false
    }

    @Ignore
    def "copies file to cache when content has changed and usage is unknown"() {
        expect: false
    }

    @Ignore
    def "reuses directory from its original location when usage is unknown"() {
        expect: false
    }

    @Ignore
    def "copies file to cache when usage is build logic"() {
        expect: false
    }

    @Ignore
    def "copies directory to cache when usage is build logic"() {
        expect: false
    }

    @Ignore
    def "can convert a classpath to cached jars"() {
        given:
        File externalFile = testDir.file("external/file1").createFile()
        File externalFileCached = cachedDir.file("file1").createFile()
        File alreadyCachedFile = cachedDir.file("file2").createFile()
        File cachedInOtherStore = otherStore.file("file3").createFile()
        File externalDir = testDir.file("external/dir1").createDir()
        def classPath = DefaultClassPath.of([externalFile, alreadyCachedFile, cachedInOtherStore, externalDir])

        when:
        def cachedClassPath = transformer.transform(classPath, BuildLogic)

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> externalFileCached

        and:
        cachedClassPath.asFiles == [externalFileCached, alreadyCachedFile, cachedInOtherStore, externalDir]
    }

    @Ignore
    def "can convert a url collection to cached jars"() {
        given:
        def externalFile = testDir.file("external/file1").createFile()
        def cachedFile = cachedDir.file("file1").createFile()
        def alreadyCachedFile = cachedDir.file("file2").createFile().toURI().toURL()
        def externalDir = testDir.file("external/dir").createDir().toURI().toURL()
        def httpURL = new URL("http://some.where.com")

        when:
        def cachedUrls = transformer.transform([externalFile.toURI().toURL(), httpURL, alreadyCachedFile, externalDir], BuildLogic)

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile

        and:
        cachedUrls == [cachedFile.toURI().toURL(), httpURL, alreadyCachedFile, externalDir]
    }

    @Ignore
    def "touches immediate children of cache dir when accessed"() {
        given:
        def externalFile = testDir.file("external/file1").createFile()
        def cacheFileChecksumDir = cachedDir.file("e11f1cf5681161f98a43c55e341f1b93")
        def cachedFile = cacheFileChecksumDir.file("sub/file1").createFile()
        def alreadyCachedFile = cachedDir.file("file2").createFile()
        def cachedInOtherStore = otherStore.file("file3").createFile()

        when:
        transformer.transform(DefaultClassPath.of([externalFile, alreadyCachedFile, cachedInOtherStore]), BuildLogic)

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile
        1 * fileAccessTimeJournal.setLastAccessTime(cacheFileChecksumDir, _)
        1 * fileAccessTimeJournal.setLastAccessTime(alreadyCachedFile, _)
        0 * fileAccessTimeJournal._
    }

    void jar(TestFile file) {
        file.withOutputStream { outstr ->
            def stream = new ZipOutputStream(outstr)
            def entry = new ZipEntry("a.class")
            entry.lastModifiedTime = FileTime.fromMillis(2000)
            stream.putNextEntry(entry)
            stream.write("class".bytes)
            stream.flush()
        }
    }
}
