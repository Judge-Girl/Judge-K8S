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

package tw.waterball.judgegirl.primitives.problem;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.io.FilenameUtils;

import javax.validation.constraints.Size;

import static tw.waterball.judgegirl.commons.utils.validations.ValidationUtils.validate;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@EqualsAndHashCode
@ToString
@Getter
public class SubmittedCodeSpec {
    @NonNull
    private final Language format;
    @Size(min = 1, max = 80)
    private final String fileName;

    public SubmittedCodeSpec(Language format, String fileName) {
        this.format = format;
        this.fileName = fileName;
        validate(this);
    }

    public String getFileExtension() {
        return FilenameUtils.getExtension(fileName);
    }
}
