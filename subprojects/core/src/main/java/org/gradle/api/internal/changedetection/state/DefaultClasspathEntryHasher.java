/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class DefaultClasspathEntryHasher implements ClasspathEntryHasher {
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR = new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };
    private static final byte[] SIGNATURE = Hashing.md5().hashString(DefaultClasspathEntryHasher.class.getName(), Charsets.UTF_8).asBytes();
    private final ClasspathContentHasher classpathContentHasher;

    public DefaultClasspathEntryHasher(ClasspathContentHasher classpathContentHasher) {
        this.classpathContentHasher = classpathContentHasher;
    }

    @Override
    public HashCode hash(FileDetails fileDetails) {
        if (fileDetails.getType() == FileType.Directory || fileDetails.getType() == FileType.Missing) {
            return null;
        }

        String name = fileDetails.getName();
        final Hasher hasher = createHasher();
        if (FileUtils.isJar(name)) {
            return hashJar(fileDetails, hasher, classpathContentHasher);
        } else {
            return hashFile(fileDetails, hasher, classpathContentHasher);
        }
    }

    @Override
    public List<FileDetails> hashDir(List<FileDetails> fileDetails) {
        // Collect the signatures of each class file
        List<FileDetails> sorted = new ArrayList<FileDetails>(fileDetails.size());
        for (FileDetails details : fileDetails) {
            if (details.getType() == FileType.RegularFile) {
                HashCode signatureForClass = hash(details);
                if (signatureForClass == null) {
                    // Should be excluded
                    continue;
                }
                sorted.add(details.withContentHash(signatureForClass));
            }
        }

        // Sort as their order is not important
        Collections.sort(sorted, FILE_DETAILS_COMPARATOR);
        return sorted;
    }

    private Hasher createHasher() {
        return new TrackingHasher(Hashing.md5().newHasher().putBytes(SIGNATURE));
    }

    private HashCode hashJar(FileDetails fileDetails, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        File zipFilePath = new File(fileDetails.getPath());
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zipFilePath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            // Ensure we visit the zip entries in a deterministic order
            Map<String, ZipEntry> entriesByName = new TreeMap<String, ZipEntry>();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    entriesByName.put(zipEntry.getName(), zipEntry);
                }
            }
            // TODO: This does not property handle duplicates inside a jar.
            for (ZipEntry zipEntry : entriesByName.values()) {
                visit(zipFile, zipEntry, hasher, classpathContentHasher);
            }
            return hasher.hash();
        } catch (ZipException e) {
            DeprecationLogger.nagUserWith("Malformed jar [" + fileDetails.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
            return hashFile(fileDetails, hasher, classpathContentHasher);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(zipFile);
        }
    }

    private HashCode hashFile(FileDetails fileDetails, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(fileDetails.getPath());
            classpathContentHasher.appendContent(fileDetails.getName(), inputStream, hasher);
            return hasher.hash();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private void visit(ZipFile zipFile, ZipEntry zipEntry, Hasher hasher, ClasspathContentHasher classpathContentHasher) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = zipFile.getInputStream(zipEntry);
            classpathContentHasher.appendContent(zipEntry.getName(), inputStream, hasher);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
}
