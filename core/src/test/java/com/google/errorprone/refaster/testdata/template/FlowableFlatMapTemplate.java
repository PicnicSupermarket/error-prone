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

import com.google.errorprone.refaster.ImportPolicy;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.UseImportPolicy;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import reactor.adapter.rxjava.RxJava2Adapter;
import tech.picnic.errorprone.migration.util.RxJavaReactorMigrationUtil;

/** Example */
// XXX: Rename to SingleFlatMapTemplate
public final class FlowableFlatMapTemplate<I, T extends I, O, M extends SingleSource<O>> {
  @BeforeTemplate
  Single<O> before(Single<T> single, Function<I, M> function) {
    return single.flatMap(function);
  }

  @AfterTemplate
  @UseImportPolicy(ImportPolicy.IMPORT_CLASS_DIRECTLY)
  Single<O> after(Single<T> single, Function<I, M> function) {
    return RxJava2Adapter.monoToSingle(
        RxJava2Adapter.singleToMono(single)
            .flatMap(
                v ->
                    RxJava2Adapter.singleToMono(
                        Single.wrap(
                            RxJavaReactorMigrationUtil.<I, M>toJdkFunction(function).apply(v)))));
  }
}
