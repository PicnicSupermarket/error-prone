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
public class AddDefaultMethodTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(AddDefaultMethod.class, getClass());

  @Test
  public void positive_singleToMonoClassMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  public Single<String> bar() {",
            "    return Single.just(\"value\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Single<String> bar() {",
            "    return Single.just(\"value\");",
            "  }",
            "  public Mono<String> bar_migrated() {",
            "    return Single.just(\"value\").as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_singleToMonoInterfaceMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "interface Foo {",
            "  Single<String> bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<String> bar() {",
            "    return bar_migrated().as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  default reactor.core.publisher.Mono<String> bar_migrated() {",
            "    return bar().as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_UpdateImplBecauseInterfaceIsUpdated() {
    helper
        .addInputLines(
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
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "public final class Bar {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "public final class Bar {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_rewriteInterfaceAndNonImplementingClass() {
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
        .addInputLines(
            "Bar.java",
            "public final class Bar {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "public final class Bar {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_rewriteInterfaceNotImplementation() {
    helper
        .addInputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Override",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
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
  public void positive_addNewMethodImplementationAndDeprecateOldOne() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_addNewMethodImplementationForDifficultExpression() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return String.valueOf(\"testValue\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return String.valueOf(\"testValue\");",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(String.valueOf(\"testValue\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_StringMigrationWithInterface() {
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
  public void negative_dontMigrateClassWithUndesiredReturnType() {
    helper
        .addInputLines(
            "Foo.java", "public class Foo {", "  Integer bar() {", "    return 1;", "  }", "}")
        .addOutputLines(
            "Foo.java", "public class Foo {", "  Integer bar() {", "    return 1;", "  }", "}")
        .doTest();
  }
}
