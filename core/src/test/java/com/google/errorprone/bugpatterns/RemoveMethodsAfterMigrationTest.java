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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link AddDefaultMethod}Test */
@RunWith(JUnit4.class)
public class RemoveMethodsAfterMigrationTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(RemoveMethodsAfterMigration.class, getClass());

  @Test
  public void dontRemoveUnmigratedMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return \"value\";",
            "  }",
            "}")
        .addOutputLines("Foo.java", "public final class Foo {", "}")
        .doTest();
  }

  @Test
  public void dontRemoveUsedMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return \"value\";",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "public final class Bar {",
            "  public String test() {",
            "  return new Foo().bar();",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontRemoveMigratedMethodBecauseItIsUsed() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return \"value\";",
            "  }",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"value\");",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "public final class Bar {",
            "  public String test() {",
            "  return new Foo().bar();",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void removeMigratedUnusedMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return \"value\";",
            "  }",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"value\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"value\");",
            "  }",
            "}")
        .addInputLines(
            "Bar.java",
            "public final class Bar {",
            "  public String test() {",
            "  return String.valueOf(new Foo().bar_migrated());",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }
}
