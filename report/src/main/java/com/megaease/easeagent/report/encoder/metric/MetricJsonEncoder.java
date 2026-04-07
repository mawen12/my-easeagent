/*
 * Copyright (c) 2021, MegaEase
 * All rights reserved.
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
 *
 */
package com.megaease.easeagent.report.encoder.metric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import com.megaease.easeagent.config.report.ReportConfigConst;
import com.megaease.easeagent.plugin.api.config.Config;
import com.megaease.easeagent.plugin.report.ByteWrapper;
import com.megaease.easeagent.plugin.report.EncodedData;
import com.megaease.easeagent.plugin.report.Encoder;
import com.megaease.easeagent.plugin.report.encoder.JsonEncoder;

import java.util.Map;

/**
 * 指标的 json 编码器，指标是以 map 格式进行编码的
 *
 * 其底层使用 ByteWrapper 来包装编码结果
 */
@AutoService(Encoder.class)
public class MetricJsonEncoder extends JsonEncoder<Map<String, Object>> {
    public static final String ENCODER_NAME = ReportConfigConst.METRIC_JSON_ENCODER_NAME;

    // Metric json 的编码结果会被保存在这个 key 中
    static final String ENCODED_TMP = "__agent_encoded__";

    // 使用 jackson 进行 json 的编码
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return ENCODER_NAME;
    }

    @Override
    public void init(Config config) {
        // ignored
    }

    @Override
    public int sizeInBytes(Map<String, Object> input) {
        EncodedData d;
        if (input.get(ENCODED_TMP) != null) {
            d = (EncodedData)input.get(ENCODED_TMP);
        } else {
            d = encode(input);
            input.put(ENCODED_TMP, d);
        }
        return d.size();
    }

    @Override
    public EncodedData encode(Map<String, Object> input) {
        try {
            byte[] data;
            if (input.get(ENCODED_TMP) != null) {
                data = (byte[])input.get(ENCODED_TMP);
            } else {
                data = this.objectMapper.writeValueAsBytes(input);
                input.put(ENCODED_TMP, data);
            }
            return new ByteWrapper(data);
        } catch (JsonProcessingException e) {
            // ignored
        }
        return new ByteWrapper(new byte[0]);
    }
}
