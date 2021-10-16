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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CountMethodsTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(CountMethods.class, getClass());

  @Test
  public void countSingleMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "public final class Foo {",
            "  public Flowable<String> test() {",
            "    return Flowable.just(\"\");",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void countMethodReference() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "import io.reactivex.Maybe;",
            "public final class Foo {",
            "  public Flowable<Object> test() {",
            "    Maybe.just(\"1\").map(Maybe::just).blockingGet();",
            "    return Flowable.just(2);",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void identifiers() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  private Single<Integer> sing = Single.just(1);",
            "  public Flowable<Object> test(Mono<Integer> moon) {",
            "    Maybe<Integer> mayBe = Maybe.just(1);",
            "    return Flowable.empty();",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void identifiersWithAnonymousClass() {
    helper
        .setArgs("-XepOpt:DirPrefix=SomeValue")
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import reactor.core.publisher.Mono;",
            "import reactor.core.publisher.Flux;",
            "public final class Foo {",
            "  private Single<Integer> sing = Single.just(1);",
            "  public Flowable<Object> test(Mono<Integer> moon) {",
            "    Maybe<Integer> mayBe = Maybe.just(1);",
            "    return Flowable.empty();",
            "  }",
            "interface Bar {",
            "  Mono<Integer> monoFunc();",
            "}",
            "",
            "public void test() {",
            "  Bar foo =",
            "          new Bar() {",
            "            Flux<Integer> fluxy = Flux.just(1);",
            "",
            "            @Override",
            "            public Mono<Integer> monoFunc() {",
            "              return Mono.just(1);",
            "            }",
            "          };",
            "}",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void adapterCalls() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Flowable;",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Flux;",
            "import reactor.core.publisher.Mono;",
            "",
            "public final class Foo {",
            "  private Mono<Integer> sing = RxJava2Adapter.singleToMono(Single.just(1));",
            "  public Flowable<Object> test(Mono<Integer> moon) {",
            "    Mono<Void> single = RxJava2Adapter.monoToCompletable(",
            "        RxJava2Adapter.maybeToMono(RxJava2Adapter.monoToMaybe(moon)))",
            "            .as(RxJava2Adapter::completableToMono);",
            "    return RxJava2Adapter.fluxToFlowable(Flux.empty());",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }
}
