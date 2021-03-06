/*
 * Copyright 2020 Johnny850807 (Waterball) 潘冠辰
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package tw.waterball.judgegirl.commons.utils;


import static java.util.stream.IntStream.range;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class StringUtils {
    
    public static String generateStringOfLength(char c, int length) {
        return range(0, length)
                .mapToObj(i -> String.valueOf(c))
                .reduce((p, n) -> p + n).orElseThrow();
    }

    public static String capitalize(String text) {
        if (text == null || text.length() == 0) {
            return text;
        }
        char[] chars = text.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    public static boolean isNullOrEmpty(String text) {
        return text == null || text.isEmpty();
    }

    public static boolean isNullOrBlank(String text) {
        return text == null || text.isBlank();
    }
}
