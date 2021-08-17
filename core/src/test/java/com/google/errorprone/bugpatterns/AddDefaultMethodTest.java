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
  public void dontMigrateAlreadyMigratedMethodWithParams() {
    helper
        .addInputLines(
            "Foo.java",
            "public class Foo {",
            "  @Deprecated",
            "  public String bar(String test) {",
            "    return String.valueOf(bar_migrated(test));",
            "  }",
            "",
            "  public Integer bar_migrated(String test) {",
            "    return Integer.valueOf(bar(test));",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void createBanner() {
    helper
        .addInputLines(
            "Banner.java",
            "package test.foo;",
            "public final class Banner {",
            "  private final Integer bannerId; ",
            "",
            "  public Banner(Integer bannerId) {",
            "    this.bannerId = bannerId;",
            "  }",
            "",
            "  public Integer getBannerId() {",
            "    return bannerId;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BannerRequest.java",
            "package test.foo;",
            "public final class BannerRequest {",
            "  private final Integer bannerId; ",
            "",
            "  public BannerRequest(Integer bannerId) {",
            "    this.bannerId = bannerId;",
            "  }",
            "",
            "  public Integer getBannerId() {",
            "    return bannerId;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BannerService.java",
            "package test.foo;",
            "import io.reactivex.Single;",
            "",
            "interface BannerService {",
            "  Single<Banner> createBanner(BannerRequest bannerRequest);",
            "}")
        .addOutputLines(
            "BannerService.java",
            "package test.foo;",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "",
            "interface BannerService {",
            "  @Deprecated",
            "  default io.reactivex.Single<test.foo.Banner> createBanner(test.foo.BannerRequest bannerRequest) {",
            "    return createBanner_migrated(bannerRequest).as(RxJava2Adapter::monoToSingle);",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<test.foo.Banner> createBanner_migrated(BannerRequest bannerRequest) {",
            "    return createBanner(bannerRequest).as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void singleToMonoClassMigration() {
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
            "    return bar_migrated().as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  public Mono<String> bar_migrated() {",
            "    return Single.just(\"value\").as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void singleToMonoInterfaceMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  Single<String> bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.lang.String> bar() {",
            "    return bar_migrated().as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  default reactor.core.publisher.Mono<java.lang.String> bar_migrated() {",
            "    return bar().as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void firstParameterChangeForClass() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  public Single<String> bar(String bannerId, Integer testName) {",
            "    return Single.just(bannerId);",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Single<String> bar(String bannerId, Integer testName) {",
            "    return bar_migrated(bannerId, testName).as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  public Mono<String> bar_migrated(String bannerId, Integer testName) {",
            "    return Single.just(bannerId).as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void interfaceParametersChange() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  String bar(String bannerId, Integer testName);",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar(java.lang.String bannerId, java.lang.Integer testName) {",
            "    return String.valueOf(bar_migrated(bannerId, testName));",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated(String bannerId, Integer testName) {",
            "    return Integer.valueOf(bar(bannerId, testName));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void supportMultiLineBlockWithOneReturn() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  public String bar(String name) {",
            "    Integer test = 1 + 1;",
            "    return String.valueOf(test) + name;",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  @Deprecated",
            "  public String bar(String name) {",
            "    return String.valueOf(bar_migrated(name));",
            "  }",
            "",
            "  public Integer bar_migrated(String name) {",
            "    Integer test = 1 + 1;",
            "    return Integer.valueOf(String.valueOf(test) + name);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void twoTypeParameters() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "public interface Foo {",
            "  public Single<Function<Integer, String>> bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.util.function.Function<java.lang.Integer, java.lang.String>> bar() {",
            "     return bar_migrated().as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  default reactor.core.publisher.Mono<java.util.function.Function<java.lang.Integer, java.lang.String>> bar_migrated() {",
            "     return bar().as(RxJava2Adapter::singleToMono);",
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
            "public final class Bar implements Foo {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
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
            "    return String.valueOf(bar_migrated());",
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
            "    return String.valueOf(bar_migrated());",
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
            "    return String.valueOf(bar_migrated());",
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
  public void negative_DontMigrateAlreadyMigratedInterface() {
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

  @Test
  public void migrateMaybeNumberToMonoNumber() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Maybe;",
            "public interface Foo {",
            "  Maybe<Integer> bar1();",
            "  Maybe<Number> bar2();",
            "  Maybe<Object> bar3();",
            "}")
        .addOutputLines(
            "out/Foo.java",
            "import io.reactivex.Maybe;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "",
            "public interface Foo {",
            "  @Deprecated  ",
            "  default io.reactivex.Maybe<java.lang.Integer> bar1() {",
            "    return bar1_migrated().as(RxJava2Adapter::monoToMaybe);",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<java.lang.Integer> bar1_migrated() {",
            "    return bar1().as(RxJava2Adapter::maybeToMono);",
            "  }",
            "  @Deprecated  ",
            "  default io.reactivex.Maybe<java.lang.Number> bar2() {",
            "    return bar2_migrated().as(RxJava2Adapter::monoToMaybe);",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<java.lang.Number> bar2_migrated() {",
            "    return bar2().as(RxJava2Adapter::maybeToMono);",
            "  }",
            "",
            "  Maybe<Object> bar3();",
            "}")
        .doTest();
  }

  @Test
  public void deleteOldImplMethodTheMigrationInfoIsAvailable() {
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
            "public final class Bar implements Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .addOutputLines(
            "out/Bar.java",
            "public final class Bar implements Foo {",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .doTest();
  }

  @Test // The `null` here doesn't make sense, but it is about testing the type parameters.
  @Ignore
  public void classWithTypeParameter() {
    helper
        .addInputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "public final class Test<T> {",
            "  public Single<Function<Integer, T>> test() {",
            "    return null;",
            "  }",
            "}")
        .addOutputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Test<T> {",
            "  @Deprecated",
            "  public Single<Function<Integer, T>> test() {",
            "     return null;",
            "  }",
            "  public Mono<Function<Integer,T>> test_migrated() {",
            "     return null.as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test // The `null` here doesn't make sense, but it is about testing the type parameters.
  @Ignore
  public void methodWithTypeParameterAndClassWithTypeParameter() {
    helper
        .addInputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "public final class Test<T> {",
            "  public <R> Single<Function<R, T>> test() {",
            "    return null;",
            "  }",
            "}")
        .addOutputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Test<T> {",
            "  @Deprecated",
            "  public <R> Single<Function<R, T>> test() {",
            "     return null;",
            "  }",
            "  public <R> Mono<Function<R, T>> test_migrated() {",
            "     return null.as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void linkageError() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "public class Foo {",
            "  public Flowable<String> flowable(String text) {",
            "    return Flowable.just(text, text);",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Flux;",
            "public class Foo {",
            "  @Deprecated",
            "  public Flowable<String> flowable(String text) {",
            "    return flowable_migrated(text).as(RxJava2Adapter::fluxToFlowable);",
            "  }",
            "  public Flux<String> flowable_migrated(String text) {",
            "    return Flowable.just(text, text).as(RxJava2Adapter::flowableToFlux);",
            "  }",
            "}")
        .addInputLines(
            "Bar.java",
            "import static com.google.common.collect.ImmutableList.toImmutableList;",
            "import io.reactivex.Flowable;",
            "import com.google.common.collect.ImmutableList;",
            "",
            "public final class Bar {",
            "  private Foo foo = new Foo();",
            "",
            "  public Flowable<String> baz() {",
            "    ImmutableList.of(1, 2).stream().map((e)->e + 1).collect(toImmutableList());",
            "    return foo.flowable(\"name\").map((e)->e + e);",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "import static com.google.common.collect.ImmutableList.toImmutableList;",
            "import com.google.common.collect.ImmutableList;",
            "import io.reactivex.Flowable;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Flux;",
            "",
            "public final class Bar {",
            "  private Foo foo = new Foo();",
            "",
            "  @Deprecated",
            "  public Flowable<String> baz() {",
            "     return baz_migrated().as(RxJava2Adapter::fluxToFlowable);",
            "  }",
            "",
            "  public Flux<String> baz_migrated() {",
            "    ImmutableList.of(1, 2).stream().map(e -> e + 1).collect(toImmutableList());",
            "    return foo.flowable(\"name\").map(e -> e + e).as(RxJava2Adapter::flowableToFlux);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  @Ignore // Doesn't work yet.
  public void abstractClass() {
    helper
        .addInputLines(
            "Foo.java", "public abstract class Foo {", "public abstract String bar();", "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  @Ignore // Doesn't work, IllegalArgumentException: expected one element ...
  // com.google.errorprone.BugCheckerRefactoringTestHelper.getFullyQualifiedName(BugCheckerRefactoringTestHelper.java:341).
  public void anonymousClass() {
    helper
        .addInputLines(
            "Foo.java",
            "abstract class Foo{",
            "  abstract String eat();",
            "}",
            "",
            "class TestAnonymousInner{",
            "  public static void main(String args[]){",
            "    Foo p = new Foo() {",
            "    String eat(){ return \"nice fruits\";}",
            "  };",
            "  p.eat();",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }
}
