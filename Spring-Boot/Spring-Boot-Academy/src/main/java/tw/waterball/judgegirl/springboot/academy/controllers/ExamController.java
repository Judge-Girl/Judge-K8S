package tw.waterball.judgegirl.springboot.academy.controllers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tw.waterball.judgegirl.academy.domain.repositories.ExamFilter;
import tw.waterball.judgegirl.academy.domain.usecases.exam.*;
import tw.waterball.judgegirl.commons.models.files.FileResource;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.commons.token.TokenService.Token;
import tw.waterball.judgegirl.primitives.Student;
import tw.waterball.judgegirl.primitives.exam.Exam;
import tw.waterball.judgegirl.primitives.exam.Question;
import tw.waterball.judgegirl.problemapi.views.ProblemView;
import tw.waterball.judgegirl.springboot.academy.presenters.ExamPresenter;
import tw.waterball.judgegirl.springboot.academy.presenters.*;
import tw.waterball.judgegirl.springboot.academy.view.*;
import tw.waterball.judgegirl.studentapi.clients.view.StudentView;

import java.util.List;
import java.util.stream.Collectors;

import static tw.waterball.judgegirl.commons.utils.StreamUtils.mapToList;
import static tw.waterball.judgegirl.springboot.utils.MultipartFileUtils.convertMultipartFilesToFileResources;
import static tw.waterball.judgegirl.springboot.utils.ResponseEntityUtils.respondInputStreamResource;
import static tw.waterball.judgegirl.submissionapi.clients.SubmissionApiClient.SUBMIT_CODE_MULTIPART_KEY_NAME;

@CrossOrigin
@Slf4j
@RequestMapping("/api")
@AllArgsConstructor
@RestController
public class ExamController {
    private final TokenService tokenService;
    private final ExamPresenter examPresenter;
    private final CreateExamUseCase createExamUseCase;
    private final GetExamsUseCase getExamsUseCase;
    private final GetExamProgressOverviewUseCase getExamProgressOverviewUseCase;
    private final GetExamOverviewUseCase getExamOverviewUseCase;
    private final UpdateExamUseCase updateExamUseCase;
    private final GetExamUseCase getExamUseCase;
    private final DeleteExamineesUseCase deleteExamineesUseCase;
    private final DeleteExamUseCase deleteExamUseCase;
    private final AddGroupOfExamineesUseCase addGroupOfExamineesUseCase;
    private final GetExamineesUseCase getExamineesUseCase;
    private final AddExamineesUseCase addExamineesUseCase;
    private final CreateExamTranscriptUseCase createExamTranscriptUseCase;
    private final CreateQuestionUseCase createQuestionUseCase;
    private final UpdateQuestionUseCase updateQuestionUseCase;
    private final DeleteQuestionUseCase deleteQuestionUseCase;
    private final AnswerQuestionUseCase answerQuestionUseCase;

    @PostMapping("/exams")
    public ExamView createExam(@RequestHeader("Authorization") String authorization,
                               @RequestBody CreateExamUseCase.Request request) {
        return tokenService.returnIfAdmin(authorization, token -> {
            createExamUseCase.execute(request, examPresenter);
            return examPresenter.present();
        });
    }

    @PutMapping("/exams/{examId}")
    public ExamView updateExam(@RequestHeader("Authorization") String authorization,
                               @PathVariable int examId, @RequestBody UpdateExamUseCase.Request request) {
        return tokenService.returnIfAdmin(authorization, token -> {
            request.setExamId(examId);
            updateExamUseCase.execute(request, examPresenter);
            return examPresenter.present();
        });
    }

    @GetMapping("/exams")
    public List<ExamView> getAllExams(@RequestHeader("Authorization") String authorization,
                                      @RequestParam(defaultValue = "0", required = false) int skip,
                                      @RequestParam(defaultValue = "50", required = false) int size,
                                      @RequestParam(defaultValue = "all", required = false) ExamFilter.Status status) {
        return tokenService.returnIfAdmin(authorization, token -> {
            GetExamsPresenter presenter = new GetExamsPresenter();
            getExamsUseCase.execute(ExamFilter.builder()
                    .skip(skip).size(size).status(status).build(), presenter);
            return presenter.present();
        });
    }

    @PostMapping("/exams/{examId}/problems/{problemId}")
    public QuestionView createQuestion(@RequestHeader("Authorization") String authorization,
                                       @PathVariable int examId, @PathVariable int problemId,
                                       @RequestBody CreateQuestionUseCase.Request request) {
        return tokenService.returnIfAdmin(authorization, token -> {
            request.setProblemId(problemId);
            request.setExamId(examId);
            CreateQuestionPresenter presenter = new CreateQuestionPresenter();
            createQuestionUseCase.execute(request, presenter);
            return presenter.present();
        });
    }

    @PutMapping("/exams/{examId}/problems/{problemId}")
    public void updateQuestion(@RequestHeader("Authorization") String authorization,
                               @PathVariable int examId, @PathVariable int problemId,
                               @RequestBody UpdateQuestionUseCase.Request request) {
        tokenService.ifAdminToken(authorization, token -> {
            request.setExamId(examId);
            request.setProblemId(problemId);
            updateQuestionUseCase.execute(request);
        });
    }

    @DeleteMapping("/exams/{examId}/problems/{problemId}")
    public void deleteQuestion(@RequestHeader("Authorization") String authorization,
                               @PathVariable int examId, @PathVariable int problemId) {
        tokenService.ifAdminToken(authorization, token ->
                deleteQuestionUseCase.execute(new DeleteQuestionUseCase.Request(examId, problemId)));
    }

    @PostMapping("/exams/{examId}/students")
    public List<String> addExaminees(@RequestHeader("Authorization") String authorization,
                                     @PathVariable int examId, @RequestBody List<String> emails) {
        return tokenService.returnIfAdmin(authorization, token -> {
            AddExamineesPresenter presenter = new AddExamineesPresenter();
            addExamineesUseCase.execute(new AddExamineesUseCase.Request(examId, emails), presenter);
            return presenter.present();
        });
    }

    @PostMapping("/exams/{examId}/groups")
    public void addGroupsOfExaminees(@RequestHeader("Authorization") String authorization,
                                     @PathVariable int examId,
                                     @RequestBody AddGroupOfExamineesUseCase.Request request) {
        tokenService.ifAdminToken(authorization, token -> {
            request.examId = examId;
            addGroupOfExamineesUseCase.execute(request);
        });
    }

    @DeleteMapping("/exams/{examId}/students")
    public void deleteExaminees(@RequestHeader("Authorization") String authorization,
                                @PathVariable int examId, @RequestBody List<String> emails) {
        tokenService.ifAdminToken(authorization, token ->
                deleteExamineesUseCase.execute(new DeleteExamineesUseCase.Request(examId, emails)));
    }

    @DeleteMapping("/exams/{examId}")
    public void deleteExam(@RequestHeader("Authorization") String authorization,
                           @PathVariable int examId) {
        tokenService.ifAdminToken(authorization, token -> deleteExamUseCase.execute(examId));
    }

    @GetMapping("/exams/{examId}/overview")
    public ExamOverview getExamOverview(@RequestHeader("Authorization") String authorization,
                                        @PathVariable int examId) {
        return tokenService.returnIfAdmin(authorization, token -> {
            ExamOverviewPresenter presenter = new ExamOverviewPresenter();
            getExamOverviewUseCase.execute(examId, presenter);
            return presenter.present();
        });
    }

    @GetMapping("/exams/{examId}/transcript")
    public TranscriptView createTranscript(@RequestHeader("Authorization") String authorization,
                                           @PathVariable int examId) {
        return tokenService.returnIfAdmin(authorization, token -> {
            ExamTranscriptPresenter presenter = new ExamTranscriptPresenter();
            createExamTranscriptUseCase.execute(examId, presenter);
            return presenter.present();
        });
    }

    @GetMapping(value = "/exams/{examId}/transcript/csv", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> createCsvFileOfTranscript(@RequestHeader("Authorization") String authorization,
                                                                         @PathVariable int examId) {
        return tokenService.returnIfAdmin(authorization, token -> {
            ExamTranscriptCsvFilePresenter presenter = new ExamTranscriptCsvFilePresenter();
            createExamTranscriptUseCase.execute(examId, presenter);
            return respondInputStreamResource(presenter.present());
        });
    }

    // Student-accessible APIs
    @GetMapping("/exams/{examId}")
    public ExamView getExamById(@RequestHeader("Authorization") String authorization,
                                @PathVariable int examId) {
        Token token = tokenService.parseBearerTokenAndValidate(authorization);
        getExamUseCase.execute(new GetExamUseCase.Request(examId,
                !token.isAdmin(), token.getStudentId()), examPresenter);
        return examPresenter.present();
    }

    @GetMapping("/exams/{examId}/students")
    public List<StudentView> getExaminees(@RequestHeader("Authorization") String authorization,
                                          @PathVariable int examId) {
        Token token = tokenService.parseBearerTokenAndValidate(authorization);
        ExamineesPresenter presenter = new ExamineesPresenter();
        getExamineesUseCase.execute(new GetExamineesUseCase.Request(examId,
                !token.isAdmin(), token.getStudentId()), presenter);
        return presenter.present();
    }

    @PostMapping("/exams/{examId}/problems/{problemId}/{langEnvName}/students/{studentId}/answers")
    public AnswerQuestionPresenter.View answerQuestion(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable int examId,
                                                       @PathVariable int problemId,
                                                       @PathVariable String langEnvName,
                                                       @PathVariable int studentId,
                                                       @RequestParam(SUBMIT_CODE_MULTIPART_KEY_NAME) MultipartFile[] submittedCodes) {
        return tokenService.returnIfGranted(studentId, authorization, token -> {
            AnswerQuestionPresenter presenter = new AnswerQuestionPresenter();
            List<FileResource> fileResources = convertMultipartFilesToFileResources(submittedCodes);
            answerQuestionUseCase.execute(new AnswerQuestionUseCase.Request(examId, problemId,
                    langEnvName, studentId, fileResources), presenter);
            return presenter.present();
        });
    }

    @GetMapping("/students/{studentId}/exams")
    public List<ExamView> getStudentExams(@RequestHeader("Authorization") String authorization,
                                          @PathVariable int studentId,
                                          @RequestParam(defaultValue = "0", required = false) int skip,
                                          @RequestParam(defaultValue = "50", required = false) int size,
                                          @RequestParam(defaultValue = "all", required = false) ExamFilter.Status status) {
        return tokenService.returnIfGranted(studentId, authorization, token -> {
            GetExamsPresenter presenter = new GetExamsPresenter();
            getExamsUseCase.execute(ExamFilter.studentId(studentId)
                    .skip(skip).size(size)
                    .status(status).build(), presenter);
            return presenter.present();
        });
    }

    @GetMapping("/exams/{examId}/students/{studentId}/overview")
    public ExamHome getExamProgressOverview(@RequestHeader("Authorization") String authorization,
                                            @PathVariable int examId,
                                            @PathVariable int studentId) {
        return tokenService.returnIfGranted(studentId, authorization, token -> {
            ExamHomePresenter presenter = new ExamHomePresenter();
            getExamProgressOverviewUseCase.execute(
                    new GetExamProgressOverviewUseCase.Request(examId, studentId), presenter);
            return presenter.present();
        });
    }

}


class GetExamsPresenter implements GetExamsUseCase.Presenter {
    private List<Exam> exams;

    @Override
    public void showExams(List<Exam> exams) {
        this.exams = exams;
    }

    public List<ExamView> present() {
        return exams.stream().map(ExamView::toViewModel).collect(Collectors.toList());
    }
}

class CreateQuestionPresenter implements CreateQuestionUseCase.Presenter {
    private Question question;

    @Override
    public void setProblem(ProblemView problemView) {
    }

    @Override
    public void setQuestion(Question question) {
        this.question = question;
    }

    public QuestionView present() {
        return QuestionView.toViewModel(question);
    }
}

class ExamineesPresenter implements GetExamineesUseCase.Presenter {
    private List<Student> examinees;

    @Override
    public void showExaminees(List<Student> examinees) {
        this.examinees = examinees;
    }

    public List<StudentView> present() {
        return mapToList(examinees, StudentView::toViewModel);
    }
}

class AddExamineesPresenter implements AddExamineesUseCase.Presenter {
    private List<String> errorList;

    @Override
    public void showNotFoundEmails(List<String> emails) {
        errorList = emails;
    }

    public List<String> present() {
        return errorList;
    }
}