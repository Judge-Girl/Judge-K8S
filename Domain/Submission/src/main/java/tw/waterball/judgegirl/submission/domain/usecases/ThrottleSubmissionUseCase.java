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

package tw.waterball.judgegirl.submission.domain.usecases;

import tw.waterball.judgegirl.primitives.submission.SubmissionThrottling;
import tw.waterball.judgegirl.primitives.submission.SubmissionThrottlingException;
import tw.waterball.judgegirl.submission.domain.repositories.SubmissionRepository;

import javax.inject.Named;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Named
public class ThrottleSubmissionUseCase {
    private static final long minSubmissionInterval = TimeUnit.SECONDS.toMillis(8);
    private final SubmissionRepository submissionRepository;

    public ThrottleSubmissionUseCase(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public void execute(SubmitCodeRequest request) throws SubmissionThrottlingException {
        SubmissionThrottling throttling = findSubmissionThrottlingForStudent(request)
                .orElseGet(() -> new SubmissionThrottling(request.problemId, request.studentId, 0L));
        throttling.throttle(minSubmissionInterval);
        submissionRepository.saveSubmissionThrottling(throttling);
    }

    private Optional<SubmissionThrottling> findSubmissionThrottlingForStudent(SubmitCodeRequest request) {
        return submissionRepository.findSubmissionThrottling(
                request.getProblemId(), request.getStudentId());
    }
}
