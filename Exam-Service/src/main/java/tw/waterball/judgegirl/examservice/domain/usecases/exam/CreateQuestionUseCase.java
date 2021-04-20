package tw.waterball.judgegirl.examservice.domain.usecases.exam;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.waterball.judgegirl.commons.exceptions.NotFoundException;
import tw.waterball.judgegirl.entities.exam.Question;
import tw.waterball.judgegirl.examservice.domain.repositories.ExamRepository;
import tw.waterball.judgegirl.problemapi.views.ProblemView;
import tw.waterball.judgegirl.studentapi.clients.ProblemServiceDriver;

import javax.inject.Named;

@Named
@AllArgsConstructor
public class CreateQuestionUseCase {
    private final ExamRepository examRepository;
    private final ProblemServiceDriver problemServiceDriver;

    public void execute(Request request, Presenter presenter) throws NotFoundException {
        Question question = new Question(request.examId, request.problemId, request.quota, request.score, request.questionOrder);
        ProblemView problem = problemServiceDriver.getProblem(request.problemId);

        examRepository.addQuestion(question);

        presenter.setProblem(problem);
        presenter.setQuestion(question);
    }

    public interface Presenter {
        void setProblem(ProblemView problemView);

        void setQuestion(Question question);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        public int examId;
        public int problemId;
        public int quota;
        public int score;
        public int questionOrder;
    }

}
