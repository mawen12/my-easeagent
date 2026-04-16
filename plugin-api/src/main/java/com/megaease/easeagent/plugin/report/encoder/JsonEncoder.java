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
package com.megaease.easeagent.plugin.report.encoder;

import com.megaease.easeagent.plugin.report.ByteWrapper;
import com.megaease.easeagent.plugin.report.EncodedData;
import com.megaease.easeagent.plugin.report.Encoder;

import java.util.List;

/**
 * JSON 编码器，用于将任意数据编码为 JSON 形式的字节数组，然后保存到 EncodedData 中。
 *
 * JSON Encoder
 * @param <T> abstract type
 */
public abstract class JsonEncoder<T> implements Encoder<T> {
    @Override
    public EncodedData encodeList(List<EncodedData> encodedItems) {
        int sizeOfArray = 2;
        int length = encodedItems.size();
        for (int i = 0; i < length; ) {
            sizeOfArray += encodedItems.get(i++).size();
            if (i < length) sizeOfArray++;
        }

        byte[] buf = new byte[sizeOfArray];
        int pos = 0;
        buf[pos++] = '[';
        for (int i = 0; i < length; ) {
            byte[] v = encodedItems.get(i++).getData();
            System.arraycopy(v, 0, buf, pos, v.length);
            pos += v.length;
            if (i < length) buf[pos++] = ',';
        }
        buf[pos] = ']';
        return new ByteWrapper(buf);
    }

    /**
     * 计算多个元素组成的消息包大小，这些元素需要被编码为 JSON 格式， [xxx,xxx]，本质上是计算加上前后括号，逗号的整体长度
     *
     * @param sizes the size list of encoded items
     * @return
     */
    @Override
    public int packageSizeInBytes(List<Integer> sizes) {
        // 前后括号
        int sizeInBytes = 2; // brackets

        if (sizes != null && !sizes.isEmpty()) {
            for (Integer size : sizes) {
                sizeInBytes += size;
                sizeInBytes++; // 逗号
            }
            sizeInBytes--; // 最后一个不需要逗号
        }

        return sizeInBytes;
    }

    @Override
    public int appendSizeInBytes(int newMsgSize) {
        return newMsgSize + 1;
    }
}
