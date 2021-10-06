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

/** {@link RemoveOldMethods}Test */
@RunWith(JUnit4.class)
public class RemoveOldMethodsTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(RemoveOldMethods.class, getClass());

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
  public void removeMethodSimpleCase() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Single<String> bar() {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  public Mono<String> bar_migrated() {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  public Mono<String> bar_migrated() {",
            "   return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void removeMethodWithParams() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Single<String> bar(String string, Integer integer) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(string, integer));",
            "  }",
            "  public Mono<String> bar_migrated(String string, Integer integer) {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  public Mono<String> bar_migrated(String string, Integer integer) {",
            "   return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void removeMethodsInterface() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  @Deprecated",
            "  default Single<String> bar() {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  default Mono<String> bar_migrated() {",
            "    return RxJava2Adapter.singleToMono(bar());",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  Mono<String> bar_migrated();",
            "}")
        .doTest();
  }

  @Test
  public void removeMethodsInterfaceWithParams() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  @Deprecated",
            "  default Single<String> bar(String string, Integer integer) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(string, integer));",
            "  }",
            "  default Mono<String> bar_migrated(String string, Integer integer) {",
            "    return RxJava2Adapter.singleToMono(bar(string, integer));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  Mono<String> bar_migrated(String string, Integer integer);",
            "}")
        .doTest();
  }

  @Test
  public void dontRemoveDefaultImplIfItIsNotFromTheMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  @Deprecated",
            "  default Single<String> bar() {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  default Mono<String> bar_migrated() {",
            "    return Mono.just(\"\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  default Mono<String> bar_migrated() {",
            "    return Mono.just(\"\");",
            "  }",
            "}")
        .addInputLines(
            "Bar.java",
            "import reactor.core.publisher.Mono;",
            "public interface Bar {",
            "  default Mono<String> bar() {",
            "    return Mono.just(\"\");",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontRemoveSoloReactorMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  Mono<String> foo();",
            "}")
        .expectUnchanged()
        .doTest();
  }

  // Add interface with default impl that must not be totally removed.
}
