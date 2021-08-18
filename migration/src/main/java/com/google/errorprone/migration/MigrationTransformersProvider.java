/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.migration;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.stream.Stream;

/**
 * The MigrationTransformersLoader is responsible for retrieving the .migration files.
 */
public final class MigrationTransformersProvider {

  public static ImmutableList<MigrationCodeTransformer> loadMigrationTransformers() {
    ImmutableList.Builder<MigrationCodeTransformer> migrations = new ImmutableList.Builder<>();
    // XXX: Make nice. Potential API:
    // 1. Accept `ErrorProneFlags` specifying paths.
    // 2. Fall back to classpath scanning, just like RefasterCheck.
    // Or:
    // Completely follow RefasterCheck; use regex flag to black-/whitelist.
    // Or:
    // Accept single `CompositeCodeTransformer`?
    // Argument against blanket classpath scanning: only some combinations may make sense?
    ImmutableList<String> migrationDefinitionUris =
        ImmutableList.of(
            "com/google/errorprone/migration_resources/SingleToMono.migration",
            "com/google/errorprone/migration_resources/FlowableToFlux.migration",
            "com/google/errorprone/migration_resources/MaybeNumberToMonoNumber.migration",
            "com/google/errorprone/migration_resources/AlsoStringToIntegerSecond.migration");

    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    for (String migrationDefinitionUri : migrationDefinitionUris) {
      URL resource = classLoader.getResource(migrationDefinitionUri);
      if (resource == null) {
        continue;
      }
      try (FileInputStream is = new FileInputStream(resource.getPath());
          ObjectInputStream ois = new ObjectInputStream(is)) {
        migrations.addAll(
            unwrap((CodeTransformer) ois.readObject())
                .filter(MigrationCodeTransformer.class::isInstance)
                .map(MigrationCodeTransformer.class::cast)
                .collect(toImmutableList()));
      } catch (IOException | ClassNotFoundException e) {
        // XXX: @Stephan, which exception to throw here?
        throw new IllegalStateException("Failed to read the Refaster migration template", e);
      }
    }
    return migrations.build();
  }

  // XXX: depending on decision above we don't need this.
  private static Stream<CodeTransformer> unwrap(CodeTransformer codeTransformer) {
    if (!(codeTransformer instanceof CompositeCodeTransformer)) {
      return Stream.of(codeTransformer);
    }

    return ((CompositeCodeTransformer) codeTransformer)
        .transformers().stream().flatMap(MigrationTransformersProvider::unwrap);
  }
}
