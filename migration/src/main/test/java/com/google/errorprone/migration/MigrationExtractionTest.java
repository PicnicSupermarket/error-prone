/*
 * Copyright 2014 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.migration;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.SerializableTester;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Refaster templates.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@RunWith(JUnit4.class)
public class MigrationExtractionTest
    extends com.google.errorprone.migration.MigrationCompilerBasedTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  // XXX: I know this test doesn't really test the serializability because it is already serialized
  // before it is a .migration file.
  @Test
  public void testReserializationOfMigrationTemplate() {
    String migrationTemplate =
        "../migration/src/main/java/com/google/errorprone/migration/templates/SingleToMono.migration";

    MigrationCodeTransformer migrationDefinition = null;
    try (FileInputStream is = new FileInputStream(migrationTemplate);
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
            "../migration/src/main/java/com/google/errorprone/migration/templates/FlowableToFluxMigrationTemplate.java",
            "../migration/src/main/java/com/google/errorprone/migration/templates/MaybeNumberToMonoNumberMigrationTemplate.java",
            "../migration/src/main/java/com/google/errorprone/migration/templates/SingleToMonoMigrationTemplate.java",
            "../migration/src/main/java/com/google/errorprone/migration/templates/FirstMigrationTemplate.java");
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
      assertTrue(call);
    }
  }
}
