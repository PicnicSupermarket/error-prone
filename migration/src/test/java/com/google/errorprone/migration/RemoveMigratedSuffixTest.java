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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link RemoveMigratedSuffix}Test */
@RunWith(JUnit4.class)
public class RemoveMigratedSuffixTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(RemoveMigratedSuffix.class, getClass());

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

  @Ignore
  @Test
  public void removeMigratedFromMethodInClass() {
    helper
        .addInputLines(
            "Foo.java",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public final class Foo {",
            "  public Flux<Integer> test_migrated() {",
            "    return Flux.just(1, 2).flatMap(i -> RxJava2Adapter.fluxToFlowable(Flux.just(i)));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public final class Foo {",
            "  public Flux<Integer> test() {",
            "    return Flux.just(1, 2).flatMap(i -> RxJava2Adapter.fluxToFlowable(Flux.just(i)));",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void nestedTypeInParam() {
    helper
        .addInputLines(
            "Foo.java",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import org.reactivestreams.Publisher;",
            "public final class Foo {",
            "  public void foo() {",
            "    requiresPublisher(RxJava2Adapter.fluxToFlowable(Flux.just(1)));",
            "  }",
            "",
            "  public void requiresPublisher(Publisher<Integer> publisher) {",
            "    return;",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import org.reactivestreams.Publisher;",
            "public final class Foo {",
            "  public void foo() {",
            "    requiresPublisher(Flux.just(1));",
            "  }",
            "",
            "  public void requiresPublisher(Publisher<Integer> publisher) {",
            "    return;",
            "  }",
            "}")
        .doTest();
  }
}
