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

package com.google.errorprone.migration_resources;

import com.google.errorprone.matchers.IsParentReturnTree;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.Matches;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import io.reactivex.Flowable;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;

public final class FlowableToFluxMigrationTemplate {
  private FlowableToFluxMigrationTemplate() {}

  static final class FlowableToFlux {
    @MigrationTemplate(value = false)
    static final class MigrateFlowableToFlux<T> {
      @BeforeTemplate
      Flowable<T> before(@Matches(IsParentReturnTree.class) Flowable<T> flowable) {
        return flowable;
      }

      @AfterTemplate
      Flux<T> after(Flowable<T> flowable) {
        return RxJava2Adapter.flowableToFlux(flowable);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateFluxToFlowable<T> {
      @BeforeTemplate
      Flux<T> before(@Matches(IsParentReturnTree.class) Flux<T> flux) {
        return flux;
      }

      @AfterTemplate
      Flowable<T> after(Flux<T> flux) {
        return RxJava2Adapter.fluxToFlowable(flux);
      }
    }
  }
}
