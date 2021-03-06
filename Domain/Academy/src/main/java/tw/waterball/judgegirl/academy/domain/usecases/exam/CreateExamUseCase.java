package tw.waterball.judgegirl.academy.domain.usecases.exam;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.waterball.judgegirl.academy.domain.repositories.ExamRepository;
import tw.waterball.judgegirl.primitives.exam.Exam;

import javax.inject.Named;
import java.util.Date;

import static tw.waterball.judgegirl.primitives.time.Duration.during;

@Named
public class CreateExamUseCase {
    private final ExamRepository examRepository;

    public CreateExamUseCase(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    public void execute(Request request, ExamPresenter presenter) throws IllegalStateException {
        Exam exam = new Exam(request.name, during(request.startTime, request.endTime), request.description);
        presenter.showExam(examRepository.save(exam));
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Request {
        public String name;
        public Date startTime;
        public Date endTime;
        public String description;
    }

}