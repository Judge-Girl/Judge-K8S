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

import tw.waterball.judgegirl.commons.exceptions.NotFoundException;
import tw.waterball.judgegirl.primitives.submission.Submission;
import tw.waterball.judgegirl.submission.domain.repositories.SubmissionRepository;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public abstract class BaseSubmissionUseCase {
    protected SubmissionRepository submissionRepository;

    public BaseSubmissionUseCase(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public Submission doFindSubmission(int studentId, String submissionId) {
        return submissionRepository.findOne(studentId, submissionId)
                .orElseThrow(() -> NotFoundException
                        .notFound(Submission.class).id(submissionId));

    }

}
