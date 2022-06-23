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

package com.google.errorprone.matchers;

import static com.google.common.truth.Truth.assertThat;

import com.google.errorprone.VisitorState;
import com.google.errorprone.scanner.Scanner;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ReturnTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IsParentReturnTreeTest extends CompilerBasedAbstractTest {
  private final List<ScannerTest> tests = new ArrayList<>();

  @After
  public void tearDown() {
    for (ScannerTest test : tests) {
      test.assertDone();
    }
  }

  @Test
  public void upperReturnTreeMatches() {
    writeFile(
        "A.java", "public class A {", "  public String foo() {", "    return \"\";", "  }", "}");

    assertCompiles(isDirectReturnTree(/* shouldMatch= */ true, new IsParentReturnTree()));
  }

  @Test
  public void nestedOnesNotMatch() {
    writeFile(
        "Foo.java",
        "import io.reactivex.Completable;",
        "import java.util.function.Function;",
        "import io.reactivex.Maybe;",
        "import io.reactivex.Single;",
        "import reactor.adapter.rxjava.RxJava2Adapter;",
        "import reactor.core.publisher.Mono;",
        "",
        "class Bar {",
        "  @Deprecated",
        "  public Maybe<String> func(Function<String, String> func) {",
        "    return RxJava2Adapter.monoToMaybe(func_migrated(func));",
        "  }",
        "  public Mono<String> func_migrated(Function<String, String> func) {",
        "    return RxJava2Adapter.maybeToMono(Maybe.just(\"3\"));",
        "  }",
        "",
        "  @Deprecated",
        "  public Completable completable(Function<String, String> func) {",
        "    return RxJava2Adapter.monoToCompletable(completable_migrated(func));",
        "  }",
        "  public Mono<Void> completable_migrated(Function<String, String> func) {",
        "    return RxJava2Adapter.completableToMono(Completable.fromAction(() -> func.apply(\"10\")));",
        "  }",
        "}",
        "",
        "public class Foo {",
        "  private Bar bar = new Bar();",
        "",
        "  public Completable remove(String param) {",
        "    return bar.func(c -> c.concat(\"2\" + param))",
        "        .switchIfEmpty(",
        "            Single.error(new IllegalArgumentException(\"\")))",
        "        .map(current -> current + current)",
        "        .flatMapCompletable(",
        "            current -> {",
        "              if (current.isEmpty()) {",
        "                return bar.completable(c -> current);",
        "              }",
        "              return bar.completable(c -> current + current);",
        "            });",
        "  }",
        "}");

    assertCompiles(isDirectReturnTree(/* shouldMatch= */ true, new IsParentReturnTree()));
  }

  private abstract static class ScannerTest extends Scanner {
    abstract void assertDone();
  }

  private Scanner isDirectReturnTree(
      final boolean shouldMatch, final Matcher<ExpressionTree> toMatch) {
    ScannerTest test =
        new ScannerTest() {
          private boolean matched = false;

          @Override
          public Void visitReturn(ReturnTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node.getExpression(), visitorState)) {
              matched = true;
            }
            return super.visitReturn(node, visitorState);
          }

          @Override
          public void assertDone() {
            assertThat(shouldMatch).isEqualTo(matched);
          }
        };
    tests.add(test);
    return test;
  }
}
