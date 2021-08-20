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

package com.google.errorprone.migration.compiler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.SerializableTester;
import com.google.errorprone.migration.MigrationCodeTransformer;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for MigrationResourceCompiler. */
@RunWith(JUnit4.class)
public class MigrationExtractionTest {

  // XXX: I know this test doesn't really test the serializability because it is already serialized
  // before it is a .migration file.
  @Test
  public void testReserializationOfMigrationTemplate() {
    String migrationTemplate = "com/google/errorprone/migration_resources/SingleToMono.migration";
    MigrationCodeTransformer migrationDefinition = null;
    ClassLoader classLoader = MigrationExtractionTest.class.getClassLoader();
    try (InputStream is = classLoader.getResourceAsStream(migrationTemplate);
        ObjectInputStream ois = new ObjectInputStream(is)) {
      migrationDefinition = (MigrationCodeTransformer) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }
    SerializableTester.reserializeAndAssert(migrationDefinition);
  }

  @Test
  public void migrationExtractionTest() {
    Assume.assumeFalse(StandardSystemProperty.OS_NAME.value().startsWith("Windows"));

    ImmutableList<String> migrationTemplates =
        ImmutableList.of(
            "com/google/errorprone/migration_resources/MaybeNumberToMonoNumberMigrationTemplate.java",
            "com/google/errorprone/migration_resources/FlowableToFluxMigrationTemplate.java",
            "com/google/errorprone/migration_resources/SingleToMonoMigrationTemplate.java",
            "com/google/errorprone/migration_resources/FirstMigrationTemplate.java");
    for (String migrationTemplate : migrationTemplates) {
      File file = new File(migrationTemplate);

      Path path = file.toPath();

      JavacFileManager fileManager = new JavacFileManager(new Context(), false, UTF_8);
      DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      JavacTask task =
          JavacTool.create()
              .getTask(
                  null,
                  fileManager,
                  diagnosticCollector,
                  ImmutableList.of("-Xplugin:MigrationResourceCompiler"),
                  ImmutableList.of(),
                  fileManager.getJavaFileObjects(path));
      Boolean call = task.call();
      InputStream resourceAsStream =
          MigrationExtractionTest.class
              .getClassLoader()
              .getResourceAsStream(
                  "com/google/errorprone/migration_resources/SingleToMono.migration");
      assertNotNull(resourceAsStream);
    }
  }
}
