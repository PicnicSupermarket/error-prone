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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;

public final class ObservableToFluxMigrationTemplate {
  private ObservableToFluxMigrationTemplate() {}

  static final class ObservableToFlux {
    @MigrationTemplate(value = false)
    static final class MigrateObservableToFlux<T> {
      @BeforeTemplate
      Observable<T> before(Observable<T> observable) {
        return observable;
      }

      @AfterTemplate
      Flux<T> after(Observable<T> observable) {
        return RxJava2Adapter.observableToFlux(observable, BackpressureStrategy.BUFFER);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateFluxToObservable<T> {
      @BeforeTemplate
      Flux<T> before(Flux<T> flux) {
        return flux;
      }

      @AfterTemplate
      Observable<T> after(Flux<T> flux) {
        return Observable.fromPublisher(flux);
      }
    }
  }
}
