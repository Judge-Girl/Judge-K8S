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

package tw.waterball.judgegirl.springboot.utils;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tw.waterball.judgegirl.commons.models.files.FileResource;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class ResponseEntityUtils {
    public static ResponseEntity<InputStreamResource> respondInputStreamResource(FileResource fileResource) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentLength(fileResource.getContentLength());
        httpHeaders.add("Content-Disposition",
                String.format("attachment; filename=\"%s\"", fileResource.getFileName()));
        return ResponseEntity.status(HttpStatus.OK).headers(httpHeaders)
                .body(new InputStreamResource(fileResource.getInputStream()));
    }
}
