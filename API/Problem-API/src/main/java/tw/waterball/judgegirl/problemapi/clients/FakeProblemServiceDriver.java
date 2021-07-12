package tw.waterball.judgegirl.problemapi.clients;

import tw.waterball.judgegirl.commons.models.files.FileResource;
import tw.waterball.judgegirl.problemapi.views.ProblemView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tw.waterball.judgegirl.commons.utils.StreamUtils.filterToList;

public class FakeProblemServiceDriver implements ProblemServiceDriver {

    private final Map<Integer, ProblemView> problems = new HashMap<>();

    @Override
    public Optional<ProblemView> getProblem(int problemId) {
        return Optional.ofNullable(problems.get(problemId));
    }

    public ProblemView addProblemView(ProblemView problemView) {
        problems.put(problemView.getId(), problemView);
        return problemView;
    }

    @Override
    public FileResource downloadProvidedCodes(int problemId, String languageEnvName, String providedCodesFileId) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileResource downloadTestCaseIOs(int problemId, String testcaseId) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ProblemView> getProblemsByIds(List<Integer> ids) {
        return filterToList(problems.values(), problem -> ids.contains(problem.getId()));
    }

    public void clear() {
        problems.clear();
    }
}
