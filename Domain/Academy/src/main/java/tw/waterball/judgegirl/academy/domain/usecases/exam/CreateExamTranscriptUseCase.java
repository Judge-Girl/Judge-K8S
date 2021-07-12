package tw.waterball.judgegirl.academy.domain.usecases.exam;

import lombok.Value;
import tw.waterball.judgegirl.academy.domain.repositories.ExamRepository;
import tw.waterball.judgegirl.commons.utils.functional.GetById;
import tw.waterball.judgegirl.primitives.Student;
import tw.waterball.judgegirl.primitives.exam.Exam;
import tw.waterball.judgegirl.primitives.exam.Examinee;
import tw.waterball.judgegirl.primitives.exam.Question;
import tw.waterball.judgegirl.primitives.exam.Record;
import tw.waterball.judgegirl.primitives.submission.Submission;
import tw.waterball.judgegirl.problemapi.clients.ProblemServiceDriver;
import tw.waterball.judgegirl.problemapi.views.ProblemView;
import tw.waterball.judgegirl.studentapi.clients.StudentServiceDriver;
import tw.waterball.judgegirl.submissionapi.clients.SubmissionServiceDriver;
import tw.waterball.judgegirl.submissionapi.views.SubmissionView;

import javax.inject.Named;
import java.util.List;

import static java.util.function.Function.identity;
import static tw.waterball.judgegirl.commons.utils.StreamUtils.*;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Named
public class CreateExamTranscriptUseCase extends AbstractExamUseCase {
    private final StudentServiceDriver studentServiceDriver;
    private final SubmissionServiceDriver submissionServiceDriver;
    private final ProblemServiceDriver problemServiceDriver;

    public CreateExamTranscriptUseCase(ExamRepository examRepository, StudentServiceDriver studentServiceDriver, SubmissionServiceDriver submissionServiceDriver, ProblemServiceDriver problemServiceDriver) {
        super(examRepository);
        this.studentServiceDriver = studentServiceDriver;
        this.submissionServiceDriver = submissionServiceDriver;
        this.problemServiceDriver = problemServiceDriver;
    }

    public void execute(int examId, Presenter presenter) {
        Exam exam = findExam(examId);
        var examineeRecords = getExamineeRecords(exam);
        presenter.showExam(exam);
        presenter.showRecords(examineeRecords);
        var problems = findProblemsByIds(getProblemIds(exam));
        presenter.showProblems(problems);
    }

    private List<ExamineeRecord> getExamineeRecords(Exam exam) {
        var questionRecords = findQuestionRecords(exam);
        var examinees = examinees(exam);
        return examineeRecords(examinees, questionRecords);
    }

    private List<QuestionRecord> findQuestionRecords(Exam exam) {
        var submissions = findSubmissions(exam);
        return zipToList(exam.getQuestions(), submissions,
                (question, submission) -> question.getProblemId() == submission.getProblemId(),
                QuestionRecord::new);
    }

    private List<Submission> findSubmissions(Exam exam) {
        return findSubmissionsByIds(getSubmissionIds(exam));
    }

    private List<Submission> findSubmissionsByIds(String[] submissionIds) {
        return mapToList(submissionServiceDriver.getSubmissions(submissionIds), SubmissionView::toEntity);
    }

    private String[] getSubmissionIds(Exam exam) {
        return mapToList(examRepository.findAllRecordsInAnExam(exam.getId()),
                Record::getSubmissionId).toArray(new String[0]);
    }

    private GetById<Integer, Student> examinees(Exam exam) {
        var idToExaminee = toMap(studentServiceDriver.getStudentsByIds(
                mapToList(exam.getExaminees(), Examinee::getStudentId)), Student::getId, identity());
        return idToExaminee::get;
    }

    private List<ExamineeRecord> examineeRecords(GetById<Integer, Student> examinees, List<QuestionRecord> records) {
        var examineeToQuestionRecords =
                groupingBy(records, questionRecord -> examinees.get(questionRecord.getStudentId()));

        return zipToList(examineeToQuestionRecords, ExamineeRecord::new);
    }

    private List<Integer> getProblemIds(Exam exam) {
        return mapToList(exam.getQuestions(), Question::getProblemId);
    }

    private List<ProblemView> findProblemsByIds(List<Integer> problemIds) {
        return problemServiceDriver.getProblemsByIds(problemIds);
    }

    public interface Presenter {
        void showExam(Exam exam);

        void showRecords(List<ExamineeRecord> examineeRecords);

        void showProblems(List<ProblemView> problems);
    }

    @Value
    public static class ExamineeRecord {
        Student examinee;
        List<QuestionRecord> questionRecords;

        public ExamineeRecord(Student examinee, List<QuestionRecord> questionRecords) {
            this.examinee = examinee;
            this.questionRecords = questionRecords;
        }
    }

    @Value
    public static class QuestionRecord {
        Question question;
        Submission record;

        public QuestionRecord(Question question, Submission record) {
            this.record = record;
            this.question = question;
        }

        public int getStudentId() {
            return record.getStudentId();
        }

        public Integer calculateScore() {
            return question.calculateScore(record);
        }
    }
}
