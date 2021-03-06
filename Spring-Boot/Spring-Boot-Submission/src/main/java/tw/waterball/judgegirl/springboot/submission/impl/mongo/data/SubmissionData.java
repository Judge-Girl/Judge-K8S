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

package tw.waterball.judgegirl.springboot.submission.impl.mongo.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document("submission")
public class SubmissionData {
    @Id
    private String id;
    private int problemId;
    private String languageEnvName;
    private int studentId;
    private VerdictData verdict;
    private String submittedCodesFileId;
    private Date submissionTime;
    private Map<String, String> bag;
    private String submittedCodesHash;

    public SubmissionData(String id, int problemId, String languageEnvName, int studentId, VerdictData verdict,
                          String submittedCodesFileId, Date submissionTime, Map<String, String> bag) {
        this.id = id;
        this.problemId = problemId;
        this.languageEnvName = languageEnvName;
        this.studentId = studentId;
        this.verdict = verdict;
        this.submittedCodesFileId = submittedCodesFileId;
        this.submissionTime = submissionTime;
        this.bag = bag;
    }
}
