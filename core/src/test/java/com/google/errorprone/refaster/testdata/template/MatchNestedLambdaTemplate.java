/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster.testdata.template;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MayOptionallyUse;
import com.google.errorprone.refaster.annotation.Placeholder;
import io.reactivex.Completable;
import org.reactivestreams.Publisher;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Mono;
import tech.picnic.errorprone.migration.util.RxJavaReactorMigrationUtil;

/** Example */
abstract class MatchNestedLambdaTemplate<T> {
  @Placeholder
  abstract Mono<?> placeholder(@MayOptionallyUse T input);

  @BeforeTemplate
  java.util.function.Function<T, Publisher<? extends Void>> before() {
    return e ->
        RxJava2Adapter.completableToMono(
            Completable.wrap(
                RxJavaReactorMigrationUtil.<T, Completable>toJdkFunction(
                        v -> RxJava2Adapter.monoToCompletable(placeholder(v)))
                    .apply(e)));
  }

  @AfterTemplate
  java.util.function.Function<T, Mono<?>> after() {
    return v -> placeholder(v);
  }
}
