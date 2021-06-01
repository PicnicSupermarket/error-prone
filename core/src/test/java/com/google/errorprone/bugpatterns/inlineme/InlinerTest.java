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

package com.google.errorprone.bugpatterns.inlineme;

import static com.google.errorprone.bugpatterns.inlineme.Inliner.PREFIX_FLAG;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.scanner.ScannerSupplier;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the {@link Inliner}. */
@RunWith(JUnit4.class)
public class InlinerTest {
  /* We expect that all @InlineMe annotations we try to use as inlineable targets are valid,
   so we run both checkers here. If the Validator trips on a method, we'll suggest some
   replacement which should trip up the checker.
  */
  private final BugCheckerRefactoringTestHelper refactoringTestHelper =
      BugCheckerRefactoringTestHelper.newInstance(
          ScannerSupplier.fromBugCheckerClasses(Inliner.class, Validator.class), getClass());

  @Test
  public void migrateMethodReference() {
    refactoringTestHelper
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getName_migrated(s))\")",
            "  public String getName(String s) {",
            "    return String.valueOf(getName_migrated(s)); ",
            "  }",
            "  public Integer getName_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Foo.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.common.collect.ImmutableList;",
            "public final class Foo {",
            "  private Client client = new Client();",
            "",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated(s))\")",
            "  public String bar(String s) {",
            "    return String.valueOf(bar_migrated(s));",
            "  }",
            "  public Integer bar_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "  public static <T, R> java.util.function.Function<T, R> toJdkFunction(",
            "    java.util.function.Function<T, R> function) {",
            "      return (t) -> {",
            "        try {",
            "          return function.apply(t);",
            "          } catch (Exception e) {",
            "            throw new IllegalArgumentException(\"BiFunction threw checked exception\", e);",
            "          }",
            "        };",
            "  }",
            "",
            "  ",
            "  public void baz() {",
            "    ImmutableList.of(\"1\", \"2\").stream().map(client::getName);",
            "    ImmutableList.of(\"1\").stream().map(e -> toJdkFunction(this::bar).apply(e));",
            "    ImmutableList.of(\"1\", \"2\").stream().map(this::bar);",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.common.collect.ImmutableList;",
            "public final class Foo {",
            "  private Client client = new Client();",
            "",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated(s))\")",
            "  public String bar(String s) {",
            "    return String.valueOf(bar_migrated(s));",
            "  }",
            "  public Integer bar_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "  public static <T, R> java.util.function.Function<T, R> toJdkFunction(",
            "    java.util.function.Function<T, R> function) {",
            "      return (t) -> {",
            "        try {",
            "          return function.apply(t);",
            "          } catch (Exception e) {",
            "            throw new IllegalArgumentException(\"BiFunction threw checked exception\", e);",
            "          }",
            "        };",
            "  }",
            "",
            "  ",
            "  public void baz() {",
            "    ImmutableList.of(\"1\", \"2\").stream().map((java.lang.String ident) -> String.valueOf(client.getName_migrated(ident)));",
            "    ImmutableList.of(\"1\").stream().map(e -> toJdkFunction((java.lang.String ident) -> String.valueOf(bar_migrated(ident))).apply(e));",
            "    ImmutableList.of(\"1\", \"2\").stream().map((java.lang.String ident) -> String.valueOf(bar_migrated(ident)));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fixVoidCases() {
    refactoringTestHelper
        .addInputLines(
            "Foo.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.noparam_migrated()\")",
            "  public String noparam() {",
            "    return noparam_migrated();",
            "  }",
            "  public String noparam_migrated() {",
            "    return \"1\";",
            "  }",
            "  public void foo() {",
            "    String s = test(this::noparam);",
            "  }",
            "  @FunctionalInterface",
            "  public interface SecuredOperation {",
            "    void call() throws Exception;",
            "  }",
            "  public String test(SecuredOperation securedOperation) {",
            "    return null;",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.noparam_migrated()\")",
            "  public String noparam() {",
            "    return noparam_migrated();",
            "  }",
            "  public String noparam_migrated() {",
            "    return \"1\";",
            "  }",
            "  public void foo() {",
            "    String s = test(() -> this.noparam_migrated());",
            "  }",
            "  @FunctionalInterface",
            "  public interface SecuredOperation {",
            "    void call() throws Exception;",
            "  }",
            "  public String test(SecuredOperation securedOperation) {",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fixBugInMethodReferenceInliner() {
    refactoringTestHelper
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getName_migrated(s))\")",
            "  public String getName(String s) {",
            "    return String.valueOf(getName_migrated(s)); ",
            "  }",
            "  public Integer getName_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Foo.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.common.collect.ImmutableList;",
            "import java.util.Comparator;",
            "",
            "public final class Foo {",
            "  private Client client = new Client();",
            "",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated(s))\")",
            "  public String bar(String s) {",
            "    return String.valueOf(bar_migrated(s));",
            "  }",
            "  public Integer bar_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "  public static <T, R> java.util.function.Function<T, R> toJdkFunction(",
            "    java.util.function.Function<T, R> function) {",
            "      return (t) -> {",
            "        try {",
            "          return function.apply(t);",
            "          } catch (Exception e) {",
            "            throw new IllegalArgumentException(\"BiFunction threw checked exception\", e);",
            "          }",
            "        };",
            "  }",
            "",
            "  ",
            "  public void baz() {",
            "     ImmutableList.of(\"1\", \"2\")",
            "        .stream()",
            "        .map(String::valueOf)",
            "        .sorted(Comparator.comparing(String::length))",
            "        .map((java.lang.String ident) -> String.valueOf(client.getName_migrated(ident)));",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontDoubleInlineInterface() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public interface Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated())\")",
            "  default String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "  default Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void migrateMethodsWithinInterfaceIfIsNotTheSameMigration() {
    refactoringTestHelper
        .addInputLines(
            "Bar.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import reactor.core.publisher.Mono;",
            "import reactor.core.publisher.Flux;",
            "",
            "public interface Bar {",
            "  @InlineMe(",
            "      replacement = \"RxJava2Adapter.fluxToFlowable(this.getAllDcs_migrated())\",",
            "      imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
            "  @Deprecated",
            "  default io.reactivex.Flowable<String> getAllDcs() {",
            "    return RxJava2Adapter.fluxToFlowable(getAllDcs_migrated());",
            "  }",
            "",
            "  default Flux<String> getAllDcs_migrated() {",
            "    return RxJava2Adapter.flowableToFlux(getAllDcs());",
            "  }",
            "  @InlineMe(",
            "  replacement = \"RxJava2Adapter.monoToSingle(this.getDc_migrated(dcId))\",",
            "  imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
            "  @Deprecated",
            "  default io.reactivex.Single<String> getDc(Integer dcId) {",
            "  return RxJava2Adapter.monoToSingle(getDc_migrated(dcId));",
            "  }",
            "  ",
            "  default Mono<String> getDc_migrated(Integer dcId) {",
            "    return RxJava2Adapter.singleToMono(",
            "    getAllDcs()",
            "      .filter(dc -> dc.equals(String.valueOf(dcId)))",
            "      .firstElement()",
            "      .switchIfEmpty(Single.error(new RuntimeException())));",
            "    }",
            "}")
        .addOutputLines(
            "Bar.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import reactor.core.publisher.Mono;",
            "import reactor.core.publisher.Flux;",
            "",
            "public interface Bar {",
            "  @InlineMe(",
            "      replacement = \"RxJava2Adapter.fluxToFlowable(this.getAllDcs_migrated())\",",
            "      imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
            "  @Deprecated",
            "  default io.reactivex.Flowable<String> getAllDcs() {",
            "    return RxJava2Adapter.fluxToFlowable(getAllDcs_migrated());",
            "  }",
            "",
            "  default Flux<String> getAllDcs_migrated() {",
            "    return RxJava2Adapter.flowableToFlux(getAllDcs());",
            "  }",
            "  @InlineMe(",
            "  replacement = \"RxJava2Adapter.monoToSingle(this.getDc_migrated(dcId))\",",
            "  imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
            "  @Deprecated",
            "  default io.reactivex.Single<String> getDc(Integer dcId) {",
            "  return RxJava2Adapter.monoToSingle(getDc_migrated(dcId));",
            "  }",
            "  ",
            "  default Mono<String> getDc_migrated(Integer dcId) {",
            "    return RxJava2Adapter.singleToMono(",
            "        RxJava2Adapter.fluxToFlowable(getAllDcs_migrated())",
            "           .filter(dc -> dc.equals(String.valueOf(dcId)))",
            "           .firstElement()",
            "           .switchIfEmpty(Single.error(new RuntimeException())));",
            "    }",
            "}")
        .doTest();
  }

  @Test
  public void nestedMigratedCallShouldMigrate() {
    refactoringTestHelper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public class Foo {",
            "  @InlineMe(",
            "    replacement = \"RxJava2Adapter.fluxToFlowable(this.getAll_migrated(activeOnly))\",",
            "    imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
            "  @Deprecated",
            "  public Flowable<String> getAll(boolean activeOnly) {",
            "    return RxJava2Adapter.fluxToFlowable(getAll_migrated(activeOnly));",
            "  }",
            "",
            "  public Flux<String> getAll_migrated(boolean activeOnly) {",
            "    return RxJava2Adapter.flowableToFlux(Flowable.empty());",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "import io.reactivex.Flowable;",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public class Bar {",
            "  public Foo foo = new Foo();",
            "",
            "  public Flux<String> getBanners_migrated(boolean activeOnly) {",
            "    return RxJava2Adapter.flowableToFlux(foo.getAll(activeOnly));",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "import io.reactivex.Flowable;",
            "import reactor.core.publisher.Flux;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public class Bar {",
            "  public Foo foo = new Foo();",
            "",
            "  public Flux<String> getBanners_migrated(boolean activeOnly) {",
            "    return RxJava2Adapter.flowableToFlux(RxJava2Adapter.fluxToFlowable(foo.getAll_migrated(activeOnly)));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateLambdaPassedAsParam() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public interface Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated())\")",
            "  default String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "  default Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Foo.java",
            "package com.google.foo;",
            "import java.util.function.Function;",
            "",
            "public class Foo implements Client {",
            "  public void baz() {",
            "    func((i) -> bar());",
            "  }",
            "  public void func(Function function) {",
            "    ",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "package com.google.foo;",
            "import java.util.function.Function;",
            "",
            "public class Foo implements Client {",
            "  public void baz() {",
            "    func((i) -> String.valueOf(bar_migrated()));",
            "  }",
            "  public void func(Function function) {",
            "    ",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void inlinerDoesNothingInAlreadyMigratedClass() {
    refactoringTestHelper
        .addInputLines(
            "Foo.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"RxJava2Adapter.monoToSingle(this.bar_migrated(s))\", imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
            "  public Single<String> bar(String s) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(s));",
            "  }",
            "  public Mono<String> bar_migrated(String s) {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"1\"));",
            "  }",
            "  ",
            "  @Deprecated",
            "  public Single<String> bar(Integer i) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(i));",
            "  }",
            "  public Mono<String> bar_migrated(Integer i) {",
            "    return RxJava2Adapter.singleToMono(Single.just(String.valueOf(i)));",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void rewriteLambda() {
    refactoringTestHelper
        .addInputLines(
            "Foo.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.common.collect.ImmutableList;",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated(s))\")",
            "  public String bar(String s) {",
            "    return String.valueOf(bar_migrated(s));",
            "  }",
            "  public Integer bar_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "  ",
            "  public void baz() {",
            "    ImmutableList.of(\"1\", \"2\").stream().map(i -> bar(i));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.common.collect.ImmutableList;",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated(s))\")",
            "  public String bar(String s) {",
            "    return String.valueOf(bar_migrated(s));",
            "  }",
            "  public Integer bar_migrated(String s) {",
            "    return Integer.valueOf(s);",
            "  }",
            "  ",
            "  public void baz() {",
            "    ImmutableList.of(\"1\", \"2\").stream().map(i -> String.valueOf(bar_migrated(i)));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void instanceMethod_withThisLiteral() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.foo2(value)\")",
            "  public void foo1(String value) {",
            "    foo2(value);",
            "  }",
            "  public void foo2(String value) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.foo1(\"frobber!\");",
            "    client.foo1(\"don't change this!\");",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.foo2(\"frobber!\");",
            "    client.foo2(\"don't change this!\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void nestedQuotes() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after(foo)\")",
            "  public String before(String foo) {",
            "    return after(foo);",
            "  }",
            "  public String after(String foo) {",
            "    return \"frobber\";",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    String result = client.before(\"\\\"\");", // "\"" - a single quote character
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    String result = client.after(\"\\\"\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void method_withParamSwap() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after(paramB, paramA)\")",
            "  public void before(String paramA, String paramB) {",
            "    after(paramB, paramA);",
            "  }",
            "  public void after(String paramB, String paramA) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import java.time.Duration;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    String a = \"a\";",
            "    String b = \"b\";",
            "    client.before(a, b);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "import java.time.Duration;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    String a = \"a\";",
            "    String b = \"b\";",
            "    client.after(b, a);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void method_withReturnStatement() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after()\")",
            "  public String before() {",
            "    return after();",
            "  }",
            "  public String after() {",
            "    return \"frobber\";",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    String result = client.before();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    String result = client.after();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void updateCallersAfterInterfaceAndMethodAreMigrated() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public interface Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated())\")",
            "  default String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "  default Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "package com.google.foo;",
            "import com.google.foo.Client;",
            "public final class Caller implements Client {",
            // the content of the `bar` was initially: return "1";
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "com/google/foo/Test.java",
            "package com.google.foo;",
            "import com.google.foo.Caller;",
            "public class Test {",
            "  public void test() {",
            "    Caller call = new Caller();",
            "    String s = call.bar();",
            "  }",
            "}")
        .addOutputLines(
            "com/google/foo/Test.java",
            "package com.google.foo;",
            "import com.google.foo.Caller;",
            "public class Test {",
            "  public void test() {",
            "    Caller call = new Caller();",
            "    String s = String.valueOf(call.bar_migrated());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void testInterfaceSuggestionWithRemovalOfImplementation() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.frobber;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public interface Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.bar_migrated())\")",
            "  default String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "ClientImpl.java",
            "package com.google.frobber;",
            "",
            "public final class ClientImpl implements Client {",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Magic.java",
            "package com.google.frobber;",
            "",
            "public final class Magic {",
            "  public void test() {",
            "    ClientImpl impl = new ClientImpl();",
            "    String s = impl.bar();",
            "  }",
            "}")
        .addOutputLines(
            "Magic.java",
            "package com.google.frobber;",
            "",
            "public final class Magic {",
            "  public void test() {",
            "    ClientImpl impl = new ClientImpl();",
            "    String s = String.valueOf(impl.bar_migrated());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void staticMethod_explicitTypeParam() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = \"Client.after()\",",
            "      imports = {\"com.google.foo.Client\"})",
            "  public static <T> T before() {",
            "    return after();",
            "  }",
            "  public static <T> T after() {",
            "    return (T) null;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "package com.google.foo;",
            "public final class Caller {",
            "  public void doTest() {",
            "    String str = Client.<String>before();",
            "  }",
            "}")
        .addOutputLines(
            "com/google/foo/Caller.java",
            "package com.google.foo;",
            "public final class Caller {",
            "  public void doTest() {",
            // TODO(b/166285406): Client.<String>after();
            "    String str = Client.after();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void instanceMethod_withConflictingImport() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "import java.time.Duration;",
            "public final class Client {",
            "  private Duration deadline = Duration.ofSeconds(5);",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = \"this.setDeadline(Duration.ofMillis(millis))\",",
            "      imports = {\"java.time.Duration\"})",
            "  public void setDeadline(long millis) {",
            "    setDeadline(Duration.ofMillis(millis));",
            "  }",
            "  public void setDeadline(Duration deadline) {",
            "    this.deadline = deadline;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import org.joda.time.Duration;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Duration jodaDuration = Duration.millis(42);",
            "    Client client = new Client();",
            "    client.setDeadline(42);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "import org.joda.time.Duration;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Duration jodaDuration = Duration.millis(42);",
            "    Client client = new Client();",
            "    client.setDeadline(java.time.Duration.ofMillis(42));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void instanceMethod_withPartiallyQualifiedInnerType() {
    refactoringTestHelper
        .addInputLines(
            "A.java",
            "package com.google;",
            "public class A {",
            "  public static class Inner {",
            "    public static void foo() {",
            "    }",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Client.java",
            "import com.google.A;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"A.Inner.foo()\", imports = \"com.google.A\")",
            "  public void something() {",
            "    A.Inner.foo();",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.something();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "import com.google.A;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    A.Inner.foo();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void instanceMethod_withConflictingMethodNameAndParameterName() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  private long deadline = 5000;",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.millis(millis)\")",
            "  public void setDeadline(long millis) {",
            "    millis(millis);",
            "  }",
            "  public void millis(long millis) {",
            "    this.deadline = millis;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.setDeadline(42);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.millis(42);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void staticMethod_withStaticImport_withImport() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.test;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = \"Client.after(value)\", ",
            "      imports = {\"com.google.test.Client\"})",
            "  public static void before(int value) {",
            "    after(value);",
            "  }",
            "  public static void after(int value) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import static com.google.test.Client.before;",
            "public final class Caller {",
            "  public void doTest() {",
            "    before(42);",
            "  }",
            "}")
        .addOutputLines(
            "Caller.java",
            "import static com.google.test.Client.before;",
            "import com.google.test.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client.after(42);",
            "  }",
            "}")
        .doTest();
  }

  // With the new suggester implementation, we always import the surrounding class, so the suggested
  // replacement here isn't considered valid.
  @Ignore("b/176439392")
  @Test
  public void staticMethod_withStaticImport_withStaticImportReplacement() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.test;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = \"after(value)\", ",
            "      staticImports = {\"com.google.test.Client.after\"})",
            "  public static void before(int value) {",
            "    after(value);",
            "  }",
            "  public static void after(int value) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import static com.google.test.Client.before;",
            "public final class Caller {",
            "  public void doTest() {",
            "    before(42);",
            "  }",
            "}")
        .addOutputLines(
            "Caller.java",
            "import static com.google.test.Client.after;",
            "import static com.google.test.Client.before;",
            "public final class Caller {",
            "  public void doTest() {",
            "    after(42);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void instanceMethodCalledBySubtype() {
    refactoringTestHelper
        .addInputLines(
            "Parent.java",
            "package com.google.test;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import java.time.Duration;",
            "public class Parent {",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = \"this.after(Duration.ofMillis(value))\", ",
            "      imports = {\"java.time.Duration\"})",
            "  protected final void before(int value) {",
            "    after(Duration.ofMillis(value));",
            "  }",
            "  protected void after(Duration value) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Child.java",
            "package com.google.test;",
            "public final class Child extends Parent {",
            "  public void doTest() {",
            "    before(42);",
            "  }",
            "}")
        .addOutputLines(
            "Child.java",
            "package com.google.test;",
            "import java.time.Duration;",
            "public final class Child extends Parent {",
            "  public void doTest() {",
            "    after(Duration.ofMillis(42));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void constructorCalledBySubtype() {
    refactoringTestHelper
        .addInputLines(
            "Parent.java",
            "package com.google.test;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import java.time.Duration;",
            "public class Parent {",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = \"this(Duration.ofMillis(value))\", ",
            "      imports = {\"java.time.Duration\"})",
            "  protected Parent(int value) {",
            "    this(Duration.ofMillis(value));",
            "  }",
            "  protected Parent(Duration value) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Child.java",
            "package com.google.test;",
            "public final class Child extends Parent {",
            "  public Child() {",
            "    super(42);",
            "  }",
            "}")
        .addOutputLines(
            "Child.java",
            "package com.google.test;",
            "import java.time.Duration;",
            "public final class Child extends Parent {",
            "  public Child() {",
            "    super(Duration.ofMillis(42));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fluentMethodChain() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.baz()\")",
            "  public Client foo() {",
            "    return baz();",
            "  }",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.baz()\")",
            "  public Client bar() {",
            "    return baz();",
            "  }",
            "  public Client baz() {",
            "    return this;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client().foo().bar();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client().baz().baz();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void inliningWithField() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "import java.time.Duration;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.setTimeout(Duration.ZERO)\", imports ="
                + " {\"java.time.Duration\"})",
            "  public void clearTimeout() {",
            "    setTimeout(Duration.ZERO);",
            "  }",
            "  public void setTimeout(Duration timeout) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    new Client().clearTimeout();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "import java.time.Duration;",
            "public final class Caller {",
            "  public void doTest() {",
            "    new Client().setTimeout(Duration.ZERO);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void returnThis() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this\")",
            "  public Client noOp() {",
            "    return this;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client = client.noOp();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client = client;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void returnThis_preChained() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this\")",
            "  public Client noOp() {",
            "    return this;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client().noOp();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void returnThis_postChained() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this\")",
            "  public Client noOp() {",
            "    return this;",
            "  }",
            "  public void bar() {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    new Client().noOp().bar();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    new Client().bar();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void returnThis_alone() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this\")",
            "  public Client noOp() {",
            "    return this;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.noOp();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void inlineUnvalidatedInline() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.errorprone.annotations.InlineMeValidationDisabled;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMeValidationDisabled(\"Migrating to factory method\")",
            "  @InlineMe(replacement = \"Client.create()\", imports = \"foo.Client\")",
            "  public Client() {}",
            "  ",
            // The Inliner wants to inline the body of this factory method to the factory method :)
            "  @SuppressWarnings(\"InlineMeInliner\")",
            "  public static Client create() { return new Client(); }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import foo.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "import foo.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = Client.create();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void inlineUnvalidatedInlineMessage() {
    CompilationTestHelper.newInstance(Inliner.class, getClass())
        .addSourceLines(
            "Client.java",
            "package foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import com.google.errorprone.annotations.InlineMeValidationDisabled;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMeValidationDisabled(\"Migrating to factory method\")",
            "  @InlineMe(replacement = \"Client.create()\", imports = \"foo.Client\")",
            "  public Client() {}",
            "  ",
            // The Inliner wants to inline the body of this factory method to the factory method :)
            "  @SuppressWarnings(\"InlineMeInliner\")",
            "  public static Client create() { return new Client(); }",
            "}")
        .addSourceLines(
            "Caller.java",
            "import foo.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    // BUG: Diagnostic contains: NOTE: this is an unvalidated inlining!"
                + " Reasoning: Migrating to factory method",
            "    Client client = new Client();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void varargs() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after(inputs)\")",
            "  public void before(int... inputs) {",
            "    after(inputs);",
            "  }",
            "  public void after(int... inputs) {}",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after(inputs)\")",
            "  public void extraBefore(int first, int... inputs) {",
            "    after(inputs);",
            "  }",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after(first)\")",
            "  public void ignoreVarargs(int first, int... inputs) {",
            "    after(first);",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.before(1);",
            "    client.before();",
            "    client.before(1, 2, 3);",
            "    client.extraBefore(42, 1);",
            "    client.extraBefore(42);",
            "    client.extraBefore(42, 1, 2, 3);",
            "    client.ignoreVarargs(42, 1);",
            "    client.ignoreVarargs(42);",
            "    client.ignoreVarargs(42, 1, 2, 3);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.after(1);",
            "    client.after();",
            "    client.after(1, 2, 3);",
            "    client.after(1);",
            "    client.after();",
            "    client.after(1, 2, 3);",
            "    client.after(42);",
            "    client.after(42);",
            "    client.after(42);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void varargsWithPrecedingElements() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.after(first, inputs)\")",
            "  public void before(int first, int... inputs) {",
            "    after(first, inputs);",
            "  }",
            "  public void after(int first, int... inputs) {}",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.before(1);",
            "    client.before(1, 2, 3);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.after(1);",
            "    client.after(1, 2, 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  @Ignore("The TODO is invalid for our code, it works...")
  public void replaceWithJustParameter() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "import java.time.Duration;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"x\")",
            "  public final int identity(int x) {",
            "    return x;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    int x = client.identity(42);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(b/180976346): replacements of the form that terminate in a parameter by itself
            //  don't work with the new replacement tool, but this is uncommon enough
            "    int x = client.identity(42);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  @Ignore("The TODO is invalid in our code, it works...")
  public void orderOfOperations() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"x * y\")",
            "  public int multiply(int x, int y) {",
            "    return x * y;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    int x = client.multiply(5, 10);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(kak): hmm, why don't we inline this?
            "    int x = client.multiply(5, 10);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  @Ignore("The TODO is invalid in our code, it works...")
  public void orderOfOperationsWithParamAddition() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"x * y\")",
            "  public int multiply(int x, int y) {",
            "    return x * y;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    int x = client.multiply(5 + 3, 10);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(kak): hmm, why don't we inline this?
            "    int x = client.multiply(5 + 3, 10);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  @Ignore("The TODO is invalid in our code, it works...")
  public void orderOfOperationsWithTrailingOperand() {
    bugCheckerWithCheckFixCompiles()
        .allowBreakingChanges()
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"x * y\")",
            "  public int multiply(int x, int y) {",
            "    return x * y;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    int x = client.multiply(5 + 3, 10) * 5;",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(kak): hmm, why don't we inline this?
            "    int x = client.multiply(5 + 3, 10) * 5;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void booleanParameterWithInlineComment() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @InlineMe(replacement = \"this.after(/* isAdmin = */ isAdmin)\")",
            "  @Deprecated",
            "  public void before(boolean isAdmin) {",
            "    after(/* isAdmin= */ isAdmin);",
            "  }",
            "  public void after(boolean isAdmin) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.before(false);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(b/189535612): this is a bug!
            "    client.after(/* false = */ false);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void trailingSemicolon() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @InlineMe(replacement = \"this.after(/* foo= */ isAdmin);;;;\")",
            "  @Deprecated",
            "  public boolean before(boolean isAdmin) {",
            "    return after(/* foo= */ isAdmin);",
            "  }",
            "  public boolean after(boolean isAdmin) { return isAdmin; }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    boolean x = (client.before(false) || true);",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    boolean x = (client.after(/* false = */ false) || true);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void customInlineMe() {
    refactoringTestHelper
        .addInputLines(
            "InlineMe.java", //
            "package bespoke;",
            "public @interface InlineMe {",
            "  String replacement();",
            "  String[] imports() default {};",
            "  String[] staticImports() default {};",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Client.java",
            "import bespoke.InlineMe;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"this.foo2(value)\")",
            "  public void foo1(String value) {",
            "    foo2(value);",
            "  }",
            "  public void foo2(String value) {",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.foo1(\"frobber!\");",
            "    client.foo1(\"don't change this!\");",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    client.foo2(\"frobber!\");",
            "    client.foo2(\"don't change this!\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void ternaryInlining_b266848535() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "import com.google.common.collect.ImmutableList;",
            "import com.google.errorprone.annotations.InlineMe;",
            "import java.util.Optional;",
            "public final class Client {",
            "  @Deprecated",
            "  @InlineMe(",
            "      replacement = ",
            "\"this.getList().isEmpty() ? Optional.empty() : Optional.of(this.getList().get(0))\",",
            "      imports = {\"java.util.Optional\"})",
            "  public Optional<String> getFoo() {",
            "    return getList().isEmpty() ? Optional.empty() : Optional.of(getList().get(0));",
            "  }",
            "  public ImmutableList<String> getList() {",
            "    return ImmutableList.of();",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import static com.google.common.truth.Truth.assertThat;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            "    assertThat(client.getFoo().get()).isEqualTo(\"hi\");",
            "  }",
            "}")
        .addOutputLines(
            "out/Caller.java",
            "import static com.google.common.truth.Truth.assertThat;",
            "import java.util.Optional;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client client = new Client();",
            // TODO(b/266848535): this is a bug; we need to add parens around the ternary
            "    assertThat(",
            "            client.getList().isEmpty() ",
            "                ? Optional.empty()",
            "                : Optional.of(client.getList().get(0)).get())",
            "        .isEqualTo(\"hi\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void varArgs_b268215956() {
    refactoringTestHelper
        .addInputLines(
            "Client.java",
            "package com.google.foo;",
            "import com.google.errorprone.annotations.InlineMe;",
            "public final class Client {",
            "  @InlineMe(",
            "      replacement = \"Client.execute2(format, args)\",",
            "      imports = {\"com.google.foo.Client\"})",
            "  public static void execute1(String format, Object... args) {",
            "    execute2(format, args);",
            "  }",
            "  public static void execute2(String format, Object... args) {",
            "    // do nothing",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Caller.java",
            "import com.google.foo.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client.execute1(\"hi %s\");",
            "  }",
            "}")
        .addOutputLines(
            "Caller.java",
            "import com.google.foo.Client;",
            "public final class Caller {",
            "  public void doTest() {",
            "    Client.execute2(\"hi %s\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontInlineMockitoExpressions() {
    refactoringTestHelper
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
            "import static org.mockito.Mockito.verify;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId()).thenReturn(\"2\");",
            "    when(foo.getId()).thenAnswer(inv -> inv.getArgument(0));",
            "    doReturn(\"2\").when(foo).getId();",
            "    doAnswer(inv -> inv.getArgument(0)).when(foo).getId();",
            "    verify(foo).getId();",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  private BugCheckerRefactoringTestHelper bugCheckerWithPrefixFlag(String prefix) {
    return BugCheckerRefactoringTestHelper.newInstance(Inliner.class, getClass())
        .setArgs("-XepOpt:" + PREFIX_FLAG + "=" + prefix);
  }

  private BugCheckerRefactoringTestHelper bugCheckerWithCheckFixCompiles() {
    return BugCheckerRefactoringTestHelper.newInstance(Inliner.class, getClass())
        .setArgs("-XepOpt:InlineMe:CheckFixCompiles=true");
  }
}
