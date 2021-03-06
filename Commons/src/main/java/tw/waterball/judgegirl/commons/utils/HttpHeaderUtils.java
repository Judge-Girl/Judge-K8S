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

import org.jetbrains.annotations.Nullable;
import tw.waterball.judgegirl.commons.exceptions.InvalidAuthorizationBearerException;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class HttpHeaderUtils {

    public static String bearerWithToken(String token) {
        return "Bearer " + token;
    }

    @Nullable
    public static String parseBearerToken(@Nullable String bearer) {
        if (bearer == null) {
            return null;
        }
        bearer = bearer.trim();
        String[] split = bearer.split("\\s+");
        if (split.length != 2 || !split[0].equalsIgnoreCase("bearer")) {
            throw new InvalidAuthorizationBearerException(bearer);
        }
        return split[1];
    }

    public static String parseFileNameFromContentDisposition(String disposition) {
        return disposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
    }
}
