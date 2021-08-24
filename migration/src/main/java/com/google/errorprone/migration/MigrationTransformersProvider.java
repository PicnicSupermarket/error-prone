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

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** The MigrationTransformersLoader is responsible for retrieving the .migration files. */
public final class MigrationTransformersProvider {

  static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      Suppliers.memoize(MigrationTransformersProvider::loadMigrationTransformers);

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
             "com/google/errorprone/migration_resources/StringToInteger.migration",
            // "com/google/errorprone/migration_resources/MaybeNumberToMonoNumber.migration",
            "com/google/errorprone/migration_resources/SingleToMono.migration",
            "com/google/errorprone/migration_resources/ObservableToFlux.migration",
            "com/google/errorprone/migration_resources/CompletableToMono.migration",
            "com/google/errorprone/migration_resources/FlowableToFlux.migration",
            "com/google/errorprone/migration_resources/MaybeToMono.migration");

    ClassLoader classLoader = MigrationTransformersProvider.class.getClassLoader();
    for (String migrationDefinitionUri : migrationDefinitionUris) {
      // https://bugs.openjdk.java.net/browse/JDK-8205976 Added retry logic for reading the
      // migration files, because there is a bug in the JDK.
      int count = 0;
      int maxTries = 3;
      while (true) {
        try (InputStream is = classLoader.getResourceAsStream(migrationDefinitionUri);
            ObjectInputStream ois = new ObjectInputStream(is)) {
          migrations.add((MigrationCodeTransformer) ois.readObject());
          //                .filter(MigrationCodeTransformer.class::isInstance)
          //                .map(MigrationCodeTransformer.class::cast)
          //                .collect(toImmutableList());
          break;
        } catch (IOException | ClassNotFoundException e) {
          if (++count == maxTries) {
            // XXX: @Stephan, which exception to throw here?
            throw new IllegalStateException(
                "Failed to read Refaster migration template: " + migrationDefinitionUri, e);
          }
        }
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
