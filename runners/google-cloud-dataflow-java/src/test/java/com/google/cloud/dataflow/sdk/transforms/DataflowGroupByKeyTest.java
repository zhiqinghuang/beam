/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.dataflow.sdk.transforms;

import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.coders.BigEndianIntegerCoder;
import com.google.cloud.dataflow.sdk.coders.KvCoder;
import com.google.cloud.dataflow.sdk.coders.StringUtf8Coder;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.runners.DataflowPipelineRunner;
import com.google.cloud.dataflow.sdk.transforms.windowing.Sessions;
import com.google.cloud.dataflow.sdk.transforms.windowing.Window;
import com.google.cloud.dataflow.sdk.util.NoopPathValidator;
import com.google.cloud.dataflow.sdk.util.WindowingStrategy;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PBegin;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.TypeDescriptor;

import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

/** Tests for {@link GroupByKey} for the {@link DataflowPipelineRunner}. */
@RunWith(JUnit4.class)
public class DataflowGroupByKeyTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  /**
   * Create a test pipeline that uses the {@link DataflowPipelineRunner} so that {@link GroupByKey}
   * is not expanded. This is used for verifying that even without expansion the proper errors show
   * up.
   */
  private Pipeline createTestServiceRunner() {
    DataflowPipelineOptions options = PipelineOptionsFactory.as(DataflowPipelineOptions.class);
    options.setRunner(DataflowPipelineRunner.class);
    options.setProject("someproject");
    options.setStagingLocation("gs://staging");
    options.setPathValidatorClass(NoopPathValidator.class);
    options.setDataflowClient(null);
    return Pipeline.create(options);
  }

  @Test
  public void testInvalidWindowsService() {
    Pipeline p = createTestServiceRunner();

    List<KV<String, Integer>> ungroupedPairs = Arrays.asList();

    PCollection<KV<String, Integer>> input =
        p.apply(Create.of(ungroupedPairs)
            .withCoder(KvCoder.of(StringUtf8Coder.of(), BigEndianIntegerCoder.of())))
        .apply(Window.<KV<String, Integer>>into(
            Sessions.withGapDuration(Duration.standardMinutes(1))));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("GroupByKey must have a valid Window merge function");
    input
        .apply("GroupByKey", GroupByKey.<String, Integer>create())
        .apply("GroupByKeyAgain", GroupByKey.<String, Iterable<Integer>>create());
  }

  @Test
  public void testGroupByKeyServiceUnbounded() {
    Pipeline p = createTestServiceRunner();

    PCollection<KV<String, Integer>> input =
        p.apply(
            new PTransform<PBegin, PCollection<KV<String, Integer>>>() {
              @Override
              public PCollection<KV<String, Integer>> apply(PBegin input) {
                return PCollection.<KV<String, Integer>>createPrimitiveOutputInternal(
                        input.getPipeline(),
                        WindowingStrategy.globalDefault(),
                        PCollection.IsBounded.UNBOUNDED)
                    .setTypeDescriptorInternal(new TypeDescriptor<KV<String, Integer>>() {});
              }
            });

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        "GroupByKey cannot be applied to non-bounded PCollection in the GlobalWindow without "
        + "a trigger. Use a Window.into or Window.triggering transform prior to GroupByKey.");

    input.apply("GroupByKey", GroupByKey.<String, Integer>create());
  }
}
