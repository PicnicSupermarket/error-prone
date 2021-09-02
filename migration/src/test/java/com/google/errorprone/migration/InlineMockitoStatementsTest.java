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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link InlineMockitoStatementsTest}Test */
@RunWith(JUnit4.class)
public class InlineMockitoStatementsTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(InlineMockitoStatements.class, getClass());

  @BeforeClass
  public static void enableTestTemplates() {
    MigrationTransformersProvider.MIGRATION_DEFINITION_URIS =
        ImmutableList.of(
            "com/google/errorprone/migration_resources/StringToInteger.migration",
            "com/google/errorprone/migration_resources/MaybeNumberToMonoNumber.migration",
            "com/google/errorprone/migration_resources/SingleToMono.migration",
            "com/google/errorprone/migration_resources/ObservableToFlux.migration",
            "com/google/errorprone/migration_resources/CompletableToMono.migration",
            "com/google/errorprone/migration_resources/MaybeToMono.migration",
            "com/google/errorprone/migration_resources/FlowableToFlux.migration");
  }

  @Test
  public void migrateMockitoWhenThenReturnAndThenAnswer() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            //            "    when(foo.getId()).thenReturn(\"2\");",
            //            "    when(foo.getId()).thenReturn(\"2\", \"3\");",
            //            "    when(foo.getId()).thenAnswer(inv -> String.valueOf(\"1\"));",
            "    when(foo.getId()).then(inv -> { ",
            "          if (true) { ",
            "            return \"4\";",
            "          }",
            "          return String.valueOf(\"5\");",
            "      });",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            //            "    when(foo.getId_migrated()).thenReturn(Integer.valueOf(\"2\"));",
            //            "    when(foo.getId_migrated()).thenReturn(Integer.valueOf(\"2\"),
            // Integer.valueOf(\"3\"));",
            //            "    when(foo.getId_migrated()).thenAnswer(inv ->
            // Integer.valueOf(String.valueOf(\"1\")));",
            "    when(foo.getId_migrated()).then(inv -> { if (true) { return \"4\"; } else { return String.valueOf(\"5\"); });",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateAllThenReturnStatements() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId()).thenReturn(\"2\").thenReturn(\"3\");",
            "    when(foo.getId()).thenReturn(\"4\").thenReturn(\"5\").thenReturn(String.valueOf(\"66\")).thenReturn(\"\" + 3);",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId_migrated())",
            "       .thenReturn(Integer.valueOf(\"2\"))",
            "       .thenReturn(Integer.valueOf(\"3\"));",
            "",
            "    when(foo.getId_migrated())",
            "       .thenReturn(Integer.valueOf(\"4\"))",
            "       .thenReturn(Integer.valueOf(\"5\"))",
            "       .thenReturn(Integer.valueOf(String.valueOf(\"66\")))",
            "       .thenReturn(Integer.valueOf(\"\" + 3));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontFailOnWhenOnlyStatement() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId());",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void migrateMockitoDoReturnAndDoAnswer() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.doReturn;",
            "import static org.mockito.Mockito.doAnswer;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    doReturn(\"2\").when(foo).getId();",
            "    doReturn(\"2\", \"3\").when(foo).getId();",
            "    doAnswer(inv -> String.valueOf(\"1\")).when(foo).getId();",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.doReturn;",
            "import static org.mockito.Mockito.doAnswer;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    doReturn(Integer.valueOf(\"2\")).when(foo).getId_migrated();",
            "    doReturn(Integer.valueOf(\"2\"), Integer.valueOf(\"3\")).when(foo).getId_migrated();",
            "    doAnswer(inv -> Integer.valueOf(String.valueOf(\"1\"))).when(foo).getId_migrated();",
            "  }",
            "}")
        .doTest();
  }

  // Example of this in Picnic-platform: when(ctarMock.findAssignmentsByTopics(any(),
  // eq(true))).thenAnswer(getTopicLookup(idByTopic));
  // dont migrate?
  @Test
  public void migrateWhenThenAnswerWithMethodInvocation() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated(s, i))\")",
            "  public String getId(String s, Integer i) {",
            "    return String.valueOf(getId_migrated(s, i));",
            "  }",
            "",
            "  public Integer getId_migrated(String s, Integer i) {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import org.mockito.stubbing.Answer;",
            "",
            "public class BarTest {",
            "  private static Answer<String> callMethod(String s) {",
            "    return inv -> { return \"Bar\" + s; };",
            "  }",
            "",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId(\"1\", 1)).thenAnswer(callMethod(\"1\"));",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontMigrateTwiceWhenThen() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId_migrated()).thenReturn(Integer.valueOf(\"2\"));",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontMigrateTwiceDoWhen() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.doReturn;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "",
            "    doReturn(Integer.valueOf(\"2\"), Integer.valueOf(\"3\")).when(foo).getId_migrated();",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void migrateVerifyStatement() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.verify;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    verify(foo).getId();",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.verify;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    verify(foo).getId_migrated();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void completableCompleteWrappedInAdapter() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import io.reactivex.Completable;",
            "import reactor.core.publisher.Mono;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"RxJava2Adapter.monoToCompletable(this.getId_migrated())\")",
            "  public Completable getId() {",
            "    return RxJava2Adapter.monoToCompletable(getId_migrated());",
            "  }",
            "",
            "  public Mono<Void> getId_migrated() {",
            "    return Mono.empty();",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.verify;",
            "import io.reactivex.Completable;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId()).thenReturn(Completable.complete());",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.verify;",
            "import static org.mockito.Mockito.when;",
            "",
            "import io.reactivex.Completable;",
            "import org.junit.Test;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId_migrated()).thenReturn(RxJava2Adapter.completableToMono(Completable.complete()));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void whenThenCallRealMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import io.reactivex.Completable;",
            "import reactor.core.publisher.Mono;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"RxJava2Adapter.monoToCompletable(this.getId_migrated())\")",
            "  public Completable getId() {",
            "    return RxJava2Adapter.monoToCompletable(getId_migrated());",
            "  }",
            "",
            "  public Mono<Void> getId_migrated() {",
            "    return Mono.empty();",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "import org.junit.Test;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId()).thenCallRealMethod();",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "import org.junit.Test;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId_migrated()).thenCallRealMethod();",
            "  }",
            "}")
        .doTest();
  }
}
