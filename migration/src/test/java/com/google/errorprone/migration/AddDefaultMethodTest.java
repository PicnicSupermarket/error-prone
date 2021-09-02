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

/** {@link AddDefaultMethod}Test */
@RunWith(JUnit4.class)
public class AddDefaultMethodTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(AddDefaultMethod.class, getClass());

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
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  public Mono<String> bar_migrated() {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
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
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  default reactor.core.publisher.Mono<java.lang.String> bar_migrated() {",
            "    return RxJava2Adapter.singleToMono(bar());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void twoMethodsSameNameDifferentParamMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "",
            "public interface Foo {",
            "  default Single<String> bar(String s) {",
            "    return Single.just(\"1\");",
            "  }",
            "",
            "  default Single<String> bar(Integer i) {",
            "    return Single.just(String.valueOf(i));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.lang.String> bar(java.lang.String s) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(s));",
            "  }",
            "  default Mono<String> bar_migrated(String s) {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"1\"));",
            "  }",
            "",
            "  @Deprecated",
            "  default io.reactivex.Single<java.lang.String> bar(java.lang.Integer i) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(i));",
            "  }",
            "  default Mono<String> bar_migrated(Integer i) {",
            "    return RxJava2Adapter.singleToMono(Single.just(String.valueOf(i)));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontMigrateTwoMigratedInterfaceMethodsWithDifferentParam() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public interface Foo {",
            "  @Deprecated",
            "  default Single<String> bar(String s) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(s));",
            "  }",
            "  default Mono<String> bar_migrated(String s) {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"1\"));",
            "  }",
            "  ",
            "  @Deprecated",
            "  default Single<String> bar(Integer i) {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated(i));",
            "  }",
            "  default Mono<String> bar_migrated(Integer i) {",
            "    return RxJava2Adapter.singleToMono(Single.just(String.valueOf(i)));",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontMigrateOverloadedClassMethodsWithInlineMe() {
    helper
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
            "  @InlineMe(replacement = \"RxJava2Adapter.monoToSingle(this.bar_migrated(i))\", imports = \"reactor.adapter.rxjava.RxJava2Adapter\")",
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
  public void customClassesAsParamAndReturnTypeRewrite() {
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
            "    return RxJava2Adapter.monoToSingle(createBanner_migrated(bannerRequest));",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<test.foo.Banner> createBanner_migrated(BannerRequest bannerRequest) {",
            "    return RxJava2Adapter.singleToMono(createBanner(bannerRequest));",
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
            "    return RxJava2Adapter.monoToSingle(bar_migrated(bannerId, testName));",
            "  }",
            "  public Mono<String> bar_migrated(String bannerId, Integer testName) {",
            "    return RxJava2Adapter.singleToMono(Single.just(bannerId));",
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
  public void rewriteSingleJavaUtilFunction() {
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
            "     return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  default reactor.core.publisher.Mono<java.util.function.Function<java.lang.Integer, java.lang.String>> bar_migrated() {",
            "     return RxJava2Adapter.singleToMono(bar());",
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
  public void addNewMethodImplementationForDifficultExpression() {
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
  public void migrateMaybeToMono() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Maybe;",
            "public final class Foo {",
            "  public Maybe<String> bar() {",
            "    return Maybe.just(\"testValue\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Maybe;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Maybe<String> bar() {",
            "    return RxJava2Adapter.monoToMaybe(bar_migrated());",
            "  }",
            "",
            "  public Mono<String> bar_migrated() {",
            "    return RxJava2Adapter.maybeToMono(Maybe.just(\"testValue\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateClassCompletableToMono() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "public class Foo {",
            "  public Completable bar() {",
            "    return Completable.fromAction(() -> {});",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public class Foo {",
            "  @Deprecated",
            "  public Completable bar() {",
            "    return RxJava2Adapter.monoToCompletable(bar_migrated());",
            "  }",
            "  public Mono<Void> bar_migrated() {",
            "    return RxJava2Adapter.completableToMono(Completable.fromAction(() -> {}));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateInterfaceCompletableToMono() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "public interface Foo {",
            "  public Completable bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            " import io.reactivex.Completable;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Completable bar() {",
            "    return RxJava2Adapter.monoToCompletable(bar_migrated());",
            "  }",
            "  default reactor.core.publisher.Mono<java.lang.Void> bar_migrated() {",
            "    return RxJava2Adapter.completableToMono(bar());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateClassObservableToFlux() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Observable;",
            "public class Foo {",
            "  public Observable<String> bar() {",
            "    return Observable.just(\"1\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.BackpressureStrategy;",
            "import io.reactivex.Observable;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Flux;",
            "",
            "public class Foo {",
            "  @Deprecated",
            "  public Observable<String> bar() {",
            "    return RxJava2Adapter.fluxToObservable(bar_migrated());",
            "  }",
            "  public Flux<String> bar_migrated() {",
            "    return RxJava2Adapter.observableToFlux(Observable.just(\"1\"), BackpressureStrategy.BUFFER);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateInterfaceObservableToFlux() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Observable;",
            "public interface Foo {",
            "  public Observable<String> bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.BackpressureStrategy;",
            "import io.reactivex.Observable;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Observable<java.lang.String> bar() {",
            "    return RxJava2Adapter.fluxToObservable(bar_migrated());",
            "  }",
            "  default reactor.core.publisher.Flux<java.lang.String> bar_migrated() {",
            "    return RxJava2Adapter.observableToFlux(bar(), BackpressureStrategy.BUFFER);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void stringMigrationInInterface() {
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
  @Ignore(
      "Only works if the MaybeNumberToMonoNumber template is on, without the MaybeToMono template.")
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
            "    return RxJava2Adapter.monoToMaybe(bar1_migrated());",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<java.lang.Integer> bar1_migrated() {",
            "    return RxJava2Adapter.monoToMaybe(bar1());",
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
  @Ignore("The BugPattern UnusedMethod takes care of the deletion.")
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
            "  @Override",
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

  @Test
  public void dontDeleteMethodAnnotatedWithOtherThenDeprecatedAndOverride() {
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
            "  @SuppressWarnings(value = \"\")",
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
        .expectUnchanged()
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
  public void multiLineRewriteWhileCallingMethodFromOtherClass() {
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
            "    return RxJava2Adapter.fluxToFlowable(flowable_migrated(text));",
            "  }",
            "  public Flux<String> flowable_migrated(String text) {",
            "    return RxJava2Adapter.flowableToFlux(Flowable.just(text, text));",
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
            "    ImmutableList.of(1, 2).stream().map((e)-> e + 1).collect(toImmutableList());",
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
            "     return RxJava2Adapter.fluxToFlowable(baz_migrated());",
            "  }",
            "",
            "  public Flux<String> baz_migrated() {",
            "    ImmutableList.of(1, 2).stream().map(e -> e + 1).collect(toImmutableList());",
            "    return RxJava2Adapter.flowableToFlux(foo.flowable(\"name\").map(e -> e + e));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void abstractClass() {
    helper
        .addInputLines(
            "Foo.java",
            "public abstract class Foo {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public abstract class Foo {",
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

  @Test
  public void rewriteAllReturnStatements() {
    helper
        .addInputLines(
            "Foo.java",
            "public abstract class Foo {",
            "  public String bar() {",
            "    if (true) {",
            "      return \"1\";",
            "    }",
            "    return \"2\";",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public abstract class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    if (true) {",
            "      return Integer.valueOf(\"1\");",
            "    }",
            "    return Integer.valueOf(\"2\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void rewriteOnlyReturnStatementsNotAllMatchingTypesInMethodBody() {
    helper
        .addInputLines(
            "Foo.java",
            "public abstract class Foo {",
            "  public String bar() {",
            "    String s = \"0\";",
            "    if (true) {",
            "      return \"1\";",
            "    }",
            "    return \"2\";",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public abstract class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    String s = \"0\";",
            "    if (true) {",
            "      return Integer.valueOf(\"1\");",
            "    }",
            "    return Integer.valueOf(\"2\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontRewriteReturnTreesInLambdas() {
    helper
        .addInputLines(
            "Foo.java",
            "import com.google.common.collect.ImmutableList;",
            "public class Foo {",
            "  public String bar() {",
            "    return ImmutableList.of(1, 2).stream()",
            "      .map(",
            "          e -> {",
            "            if (true) {",
            "              return \"2\";",
            "            }",
            "            return \"3\";",
            "         })",
            "      .findFirst().get();",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import com.google.common.collect.ImmutableList;",
            "public class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(ImmutableList.of(1, 2).stream()",
            "      .map(",
            "          e -> {",
            "            if (true) {",
            "              return \"2\";",
            "            }",
            "            return \"3\";",
            "         })",
            "      .findFirst().get());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontRewriteReturnInLambdaOfTypeCompletable() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "public class Foo {",
            "  public Completable bar(String s) {",
            "     return Maybe.just(s)",
            "       .switchIfEmpty(Single.error(new IllegalArgumentException()))",
            "       .map(e -> e + e)",
            "       .flatMapCompletable(",
            "             current -> {",
            "                if (true) {",
            "                  return Completable.fromAction(() -> current.concat(current));",
            "                }",
            "                return Completable.complete();",
            "       });",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "",
            "public class Foo {",
            "  @Deprecated",
            "  public Completable bar(String s) {",
            "    return RxJava2Adapter.monoToCompletable(bar_migrated(s));",
            "  }",
            "",
            "  public Mono<Void> bar_migrated(String s) {",
            "    return RxJava2Adapter.completableToMono(Maybe.just(s)",
            "       .switchIfEmpty(Single.error(new IllegalArgumentException()))",
            "       .map(e -> e + e)",
            "       .flatMapCompletable(",
            "           current -> {",
            "           if (true) {",
            "             return Completable.fromAction(() -> current.concat(current));",
            "           }",
            "           return Completable.complete();",
            "    }));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontRewriteLambdasThatCallMethodsAndPassFunctions() {
    helper
        .addInputLines(
            "Bar.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "",
            "public class Bar {",
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
            "}")
        .expectUnchanged()
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
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
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Completable;",
            "import io.reactivex.Maybe;",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "",
            "public class Foo {",
            "  private Bar bar = new Bar();",
            "",
            "  @Deprecated",
            "  public Completable remove(String param) {",
            "    return RxJava2Adapter.monoToCompletable(remove_migrated(param));",
            "  }",
            "",
            "  public Mono<Void> remove_migrated(String param) {",
            "    return RxJava2Adapter.completableToMono(bar.func(c -> c.concat(\"2\" + param))",
            "        .switchIfEmpty(",
            "            Single.error(new IllegalArgumentException(\"\")))",
            "        .map(current -> current + current)",
            "        .flatMapCompletable(",
            "            current -> {",
            "              if (current.isEmpty()) {",
            "                return bar.completable(c -> current);",
            "              }",
            "              return bar.completable(c -> current + current);",
            "            }));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void reuseDefaultImplementation() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  default Single<String> bar() {",
            "    return Single.just(\"1\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.lang.String> bar() {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "",
            "  default Mono<String> bar_migrated() {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"1\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void reuseDefaultImplementationWithMultipleReturns() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  default Single<String> bar() {",
            "    Single.just(\"2\");",
            "    if (true) {",
            "      return Single.error(new RuntimeException(\"Error!\"));",
            "    }",
            "    return Single.just(\"1\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.lang.String> bar() {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "",
            "  default Mono<String> bar_migrated() {",
            "    Single.just(\"2\");",
            "    if (true) {",
            "      return RxJava2Adapter.singleToMono(Single.error(new RuntimeException(\"Error!\")));",
            "    }",
            "    return RxJava2Adapter.singleToMono(Single.just(\"1\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void dontMigrateRefasterMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "import com.google.errorprone.refaster.annotation.BeforeTemplate;",
            "import com.google.errorprone.refaster.annotation.AfterTemplate;",
            "",
            "public final class Foo {",
            "  @BeforeTemplate",
            "  public String before(String s) {",
            "    return s;",
            "  }",
            "",
            "  @AfterTemplate",
            "  public String after(String s) {",
            "    return s + s;",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void dontRewriteFunctionalInterfaces() {
    helper
        .addInputLines(
            "Foo.java",
            "@FunctionalInterface",
            "public interface Foo {",
            "  String dontMigrate();",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void migratePrivateAndProtectedMethods() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "final class Foo {",
            "  Single<String> bar() {",
            "    return Single.just(\"value\");",
            "  }",
            "  private Single<String> foo() {",
            "    return Single.just(\"value\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "final class Foo {",
            "  @Deprecated",
            "  Single<String> bar() {",
            "    return RxJava2Adapter.monoToSingle(bar_migrated());",
            "  }",
            "  Mono<String> bar_migrated() {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
            "  }",
            "",
            "  @Deprecated",
            "  private Single<String> foo() {",
            "    return RxJava2Adapter.monoToSingle(foo_migrated());",
            "  }",
            "  private Mono<String> foo_migrated() {",
            "    return RxJava2Adapter.singleToMono(Single.just(\"value\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void rewriteMethodIfItHasInterfaceWithNotThisMethodDefined() {
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
            "}")
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Deprecated",
            "  @Override",
            "  public String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public String baz() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Deprecated",
            "  @Override",
            "  public String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public String baz() {",
            "    return String.valueOf(baz_migrated());",
            "  }",
            "  public Integer baz_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .doTest();
  }
}
