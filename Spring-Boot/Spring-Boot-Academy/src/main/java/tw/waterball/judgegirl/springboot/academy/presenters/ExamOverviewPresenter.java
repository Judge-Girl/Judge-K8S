package tw.waterball.judgegirl.springboot.academy.presenters;

import tw.waterball.judgegirl.academy.domain.usecases.exam.GetExamOverviewUseCase;
import tw.waterball.judgegirl.primitives.exam.Exam;
import tw.waterball.judgegirl.primitives.exam.Question;
import tw.waterball.judgegirl.primitives.problem.Problem;
import tw.waterball.judgegirl.springboot.academy.view.ExamOverview;

import java.util.ArrayList;
import java.util.List;

import static tw.waterball.judgegirl.commons.utils.StreamUtils.zipToList;

/**
 * @author - c11037at@gmail.com (snowmancc)
 */
public class ExamOverviewPresenter implements GetExamOverviewUseCase.Presenter {
    private Exam exam;
    private final List<Problem> problems = new ArrayList<>();

    @Override
    public void showExam(Exam exam) {
        this.exam = exam;
    }

    @Override
    public void showQuestion(Question question, Problem problem) {
        problems.add(problem);
    }

    public ExamOverview present() {
        return ExamOverview.builder()
                .id(exam.getId())
                .name(exam.getName())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .description(exam.getDescription())
                .questions(aggregateQuestionOverviews())
                .build();
    }

    private List<ExamOverview.QuestionItem> aggregateQuestionOverviews() {
        return zipToList(problems,
                problem -> exam.getQuestionByProblemId(problem.getId())
                        .orElseThrow(() -> new IllegalStateException("Can't find the corresponding problemId in the exam")),
                ExamOverview.QuestionItem::toViewModel);
    }

}
