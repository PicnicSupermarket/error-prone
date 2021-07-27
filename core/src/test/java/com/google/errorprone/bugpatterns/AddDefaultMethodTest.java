/*
 * Copyright 2015 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link AddDefaultMethod}Test */
@RunWith(JUnit4.class)
public class AddDefaultMethodTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(AddDefaultMethod.class, getClass());

  @Test
  public void positive_StringMigration() {
    helper
        .addInputLines("Foo.java", "interface Foo {", "  String bar();", "  Number baz();", "}")
        .addOutputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .doTest();
  }

  @Test
  public void negative_DontDoubleMigrate() {
    helper
        .addInputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void negative_dontMigrateClass() {
    helper
        .addInputLines(
            "Foo.java", "public class Foo {", "  String bar() {", "    return \"\";", "  }", "}")
        .addOutputLines(
            "Foo.java", "public class Foo {", "  String bar() {", "    return \"\";", "  }", "}")
        .doTest();
  }
}
