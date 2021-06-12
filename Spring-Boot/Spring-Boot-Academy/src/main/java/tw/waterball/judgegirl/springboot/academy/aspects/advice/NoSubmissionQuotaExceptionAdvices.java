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

package tw.waterball.judgegirl.springboot.academy.aspects.advice;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import tw.waterball.judgegirl.primitives.exam.NoSubmissionQuotaException;

import static java.lang.String.format;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoSubmissionQuotaExceptionAdvices {
    @ExceptionHandler({NoSubmissionQuotaException.class})
    public ResponseEntity<String> handleExceptions(NoSubmissionQuotaException err) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(format("{\"error\":%s}", err.getName()));
    }
}
