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

package tw.waterball.judgegirl.primitives.submission;

import lombok.*;

import java.util.Objects;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionThrottling {
    private String id;
    private int problemId;
    private int studentId;
    private Long lastSubmitTime;

    public SubmissionThrottling(int problemId, int studentId, Long lastSubmitTime) {
        this.problemId = problemId;
        this.studentId = studentId;
        this.lastSubmitTime = lastSubmitTime;
    }

    public void throttle(long minSubmissionInterval) throws SubmissionThrottlingException {
        long intervalSeconds = MILLISECONDS.toSeconds(currentTimeMillis() - getLastSubmitTime());
        long minSubmissionIntervalSeconds = MILLISECONDS.toSeconds(minSubmissionInterval);
        if (intervalSeconds < minSubmissionIntervalSeconds) {
            throw new SubmissionThrottlingException(minSubmissionIntervalSeconds - intervalSeconds);
        }
        setLastSubmitTime(currentTimeMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubmissionThrottling that = (SubmissionThrottling) o;
        return problemId == that.problemId &&
                studentId == that.studentId &&
                Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, problemId, studentId);
    }
}
