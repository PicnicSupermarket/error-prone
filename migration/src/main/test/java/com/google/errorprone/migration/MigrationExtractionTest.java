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
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
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
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tests for Refaster templates.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@RunWith(JUnit4.class)
public class MigrationExtractionTest extends MigrationCompilerBasedTest {

  @Test
  public void test() {

    String refasterUri =
        "src/main/java/com/google/errorprone/bugpatterns/FirstMigrationTemplate.refaster";
    compile(
        "class FullyQualifiedIdentExample {",
        "  public java.math.RoundingMode example() {",
        "    return java.math.RoundingMode.FLOOR;",
        "  }",
        "}");

    assertThat(false).isFalse();
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void migrationExtractionTest() throws IOException {
    Assume.assumeFalse(StandardSystemProperty.OS_NAME.value().startsWith("Windows"));

    File file =
        new File(
            "../migration/src/main/java/com/google/errorprone/migration/templates/FirstMigrationTemplate.java");
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
    assertThat(Files.readAllLines(path, UTF_8))
        .containsExactly(
            "package com.google.errorprone.migration;",
            "",
            "import com.google.errorprone.refaster.annotation.AfterTemplate;",
            "import com.google.errorprone.refaster.annotation.BeforeTemplate;",
            "import com.google.errorprone.refaster.annotation.MigrationTemplate;",
            "",
            "public final class FirstMigrationTemplate {",
            "  private FirstMigrationTemplate() {}",
            "",
            "  @MigrationTemplate(value = false)",
            "  static final class MigrateStringToInteger {",
            "    @BeforeTemplate",
            "    String before(String s) {",
            "      return s;",
            "    }",
            "",
            "    @AfterTemplate",
            "    Integer after(String s) {",
            "      return Integer.valueOf(s);",
            "    }",
            "  }",
            "",
            "  @MigrationTemplate(value = true)",
            "  static final class MigrateIntegerToString {",
            "    @BeforeTemplate",
            "    Integer before(Integer s) {",
            "      return s;",
            "    }",
            "",
            "    @AfterTemplate",
            "    String after(Integer s) {",
            "      return String.valueOf(s);",
            "    }",
            "  }",
            "}")
        .inOrder();
  }
}
