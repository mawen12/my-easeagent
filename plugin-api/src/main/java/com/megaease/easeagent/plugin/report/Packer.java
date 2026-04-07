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
package com.megaease.easeagent.plugin.report;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pack a list of encoded items into a message package.
 *
 * 将一些列编码后的元素打包成一个消息包。其本质是一个编码器。
 */
public interface Packer {
    /**
     * The encoder name
     *
     * 编码器的名称
     * /
    String name();

    /**
     * Combines a list of encoded items into an encoded list. For example, in thrift, this would be
     * length-prefixed, whereas in json, this would be comma-separated and enclosed by brackets.
     *
     * 将已编码的元素列表打包成一条元素。例如，在 thrift 中，这将是长度前缀，而在 json 中，这将是逗号分隔并由括号括起来。
     *
     * @param encodedItems encoded item
     * @return encoded list
     */
    EncodedData encodeList(List<EncodedData> encodedItems);

    /**
     * Calculate the size of a message package combined by a list of item
     *
     * 计算由元素列表组合而成的消息包的大小
     *
     *
     * @param encodedItems encodes item
     * @return size of packaged message
     */
    default int messageSizeInBytes(List<EncodedData> encodedItems) {
        return packageSizeInBytes(encodedItems.stream().map(EncodedData::size).collect(Collectors.toList()));
    }

    /**
     * Calculate the increase size when append a new message
     *
     * 计算当追加一个新消息时的增加大小
     *
     * @param newMsgSize the size of encoded message to append
     * @return the increase size of a whole message package
     */
    int appendSizeInBytes(int newMsgSize);

    /**
     * Calculate the whole message package size combined of items
     *
     * 计算各个项组成的整个消息包的大小
     *
     * @param sizes the size list of encoded items
     * @return the size of a whole message package
     */
    int packageSizeInBytes(List<Integer> sizes);
}
