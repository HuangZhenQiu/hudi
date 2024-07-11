/*
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied.  See the License for the
  * specific language governing permissions and limitations
  * under the License.
  */

package org.apache.hudi.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import java.util.Properties;
import org.apache.hudi.common.config.TypedProperties;
import org.apache.hudi.config.metrics.HoodieMetricsConfig;
import org.apache.hudi.exception.HoodieException;
import org.apache.hudi.metrics.custom.CustomizableMetricsReporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestMetricsReporterFactory {

    @Mock HoodieMetricsConfig metricsConfig;

    @Mock MetricRegistry registry;

    @Test
    public void metricsReporterFactoryShouldReturnReporter() {
        when(metricsConfig.getMetricsReporterType()).thenReturn(MetricsReporterType.INMEMORY);
        MetricsReporter reporter = MetricsReporterFactory.createReporter(metricsConfig, registry).get();
        assertTrue(reporter instanceof InMemoryMetricsReporter);
    }

    @Test
    public void metricsReporterFactoryShouldReturnUserDefinedReporter() {
        when(metricsConfig.getMetricReporterClassName())
                .thenReturn(DummyMetricsReporter.class.getName());

        TypedProperties props = new TypedProperties();
        props.setProperty("testKey", "testValue");

        when(metricsConfig.getProps()).thenReturn(props);
        MetricsReporter reporter = MetricsReporterFactory.createReporter(metricsConfig, registry).get();
        assertTrue(reporter instanceof CustomizableMetricsReporter);
        assertEquals(props, ((DummyMetricsReporter) reporter).getProps());
        assertEquals(registry, ((DummyMetricsReporter) reporter).getRegistry());
    }

    @Test
    public void metricsReporterFactoryShouldThrowExceptionWhenMetricsReporterClassIsIllegal() {
        when(metricsConfig.getMetricReporterClassName())
                .thenReturn(IllegalTestMetricsReporter.class.getName());
        when(metricsConfig.getProps()).thenReturn(new TypedProperties());
        assertThrows(
                HoodieException.class,
                () -> MetricsReporterFactory.createReporter(metricsConfig, registry));
    }

    public static class DummyMetricsReporter extends CustomizableMetricsReporter {

        public DummyMetricsReporter(Properties props, MetricRegistry registry) {
            super(props, registry);
        }

        @Override
        public void start() {}

        @Override
        public void report() {}

        @Override
        public void stop() {}
    }

    public static class IllegalTestMetricsReporter {

        public IllegalTestMetricsReporter(Properties props, MetricRegistry registry) {}
    }
}
