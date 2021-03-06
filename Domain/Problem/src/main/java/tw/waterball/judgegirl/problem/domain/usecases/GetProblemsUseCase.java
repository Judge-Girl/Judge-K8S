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

package tw.waterball.judgegirl.problem.domain.usecases;

import lombok.AllArgsConstructor;
import tw.waterball.judgegirl.primitives.problem.Problem;
import tw.waterball.judgegirl.problem.domain.repositories.ProblemQueryParams;
import tw.waterball.judgegirl.problem.domain.repositories.ProblemRepository;

import javax.inject.Named;
import java.util.List;

import static tw.waterball.judgegirl.commons.utils.StreamUtils.filterToList;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Named
@AllArgsConstructor
public class GetProblemsUseCase {
    private final ProblemRepository problemRepository;

    public void execute(ProblemQueryParams problemQueryParams, Presenter presenter) {
        presenter.showProblems(problemRepository.find(problemQueryParams));
    }

    public void execute(Request request, Presenter presenter) {
        var problems = problemRepository.findProblemsByIds(request.problemIds);
        if (!request.includeInvisibleProblems) {
            problems = filterToList(problems, Problem::getVisible);
        }
        presenter.showProblems(problems);
    }

    public interface Presenter {
        void showProblems(List<Problem> problems);
    }

    @AllArgsConstructor
    public static class Request {
        public final boolean includeInvisibleProblems;
        public final int[] problemIds;
    }

}
