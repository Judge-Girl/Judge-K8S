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

package tw.waterball.judgegirl.api.retrofit;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

import static java.util.Arrays.stream;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class RetrofitFactory {
    private final ObjectMapper objectMapper;
    private final Interceptor[] interceptors;

    public RetrofitFactory(ObjectMapper objectMapper, Interceptor... interceptors) {
        this.objectMapper = objectMapper;
        this.interceptors = interceptors;
    }

    public Retrofit create(String scheme, String host, int port, Interceptor... interceptors) {
        if (host.endsWith("/")) {
            throw new IllegalArgumentException("The base url should not end with '/'");
        }

        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        stream(this.interceptors).forEach(httpClientBuilder::addInterceptor);
        stream(interceptors).forEach(httpClientBuilder::addInterceptor);

        return new Retrofit.Builder()
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .baseUrl(String.format("%s://%s:%d", scheme, host, port))
                .client(httpClientBuilder.connectTimeout(30, TimeUnit.SECONDS).build())
                .build();
    }
}
