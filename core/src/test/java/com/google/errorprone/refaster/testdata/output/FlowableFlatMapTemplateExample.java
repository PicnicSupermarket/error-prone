/* * Copyright 2014 The Error Prone Authors. * * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file except in compliance with the License. * You may obtain a copy of the License at * * http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. * See the License for the specific language governing permissions and * limitations under the License. */ package com.google.errorprone.refaster.testdata;

import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import reactor.adapter.rxjava.RxJava2Adapter;
import tech.picnic.errorprone.migration.util.RxJavaReactorMigrationUtil;
/** Example */
public class FlowableFlatMapTemplateExample {
  void testFlowableFlatMap() {
    RxJava2Adapter.monoToSingle(
        RxJava2Adapter.singleToMono(
                Maybe.just(1)
                    .flatMapSingle(
                        new Function<Integer, SingleSource<Schema>>() {
                          @Override
                          public SingleSource<Schema> apply(@NonNull Integer integer)
                              throws Exception {
                            return Maybe.just(1)
                                .switchIfEmpty(Maybe.error(IllegalAccessError::new))
                                .flatMapSingle(
                                    new Function<Integer, SingleSource<? extends Schema>>() {
                                      @Override
                                      public SingleSource<? extends Schema> apply(
                                          @NonNull Integer integer) throws Exception {
                                        return Single.just(new Schema(1));
                                      }
                                    });
                          }
                        }))
            .flatMap(
                v ->
                    RxJava2Adapter.singleToMono(
                        Single.wrap(
                            RxJavaReactorMigrationUtil.<Schema, SingleSource<Integer>>toJdkFunction(
                                    i -> {
                                      Schema schema2 = new Schema(1);
                                      return Single.just(i.getBar() + 1);
                                    })
                                .apply(v)))));
  }

  private static class Schema {
    private final Integer bar;

    public Schema(Integer x) {
      this.bar = x;
    }

    public Integer getBar() {
      return bar;
    }
  }
}
