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

import tw.waterball.judgegirl.primitives.submission.Submission;
import tw.waterball.judgegirl.submission.domain.repositories.SubmissionRepository;
import tw.waterball.judgegirl.submission.domain.usecases.query.SubmissionQueryParams;

import javax.inject.Named;
import java.util.List;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Named
public class GetSubmissionsUseCase {
    private final SubmissionRepository submissionRepository;

    public GetSubmissionsUseCase(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public void execute(SubmissionQueryParams queryParams, Presenter presenter) {
        var submissions = submissionRepository.query(queryParams);
        presenter.showSubmissions(submissions);
    }

    public void execute(String[] submissionIds, Presenter presenter) {
        presenter.showSubmissions(submissionRepository.findAllByIds(submissionIds));
    }

    public interface Presenter {
        void showSubmissions(List<Submission> submissions);
    }
}
