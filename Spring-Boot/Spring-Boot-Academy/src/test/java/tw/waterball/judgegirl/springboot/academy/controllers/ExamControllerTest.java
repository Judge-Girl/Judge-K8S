package tw.waterball.judgegirl.springboot.academy.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.h2.tools.Server;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import tw.waterball.judgegirl.academy.domain.repositories.ExamFilter;
import tw.waterball.judgegirl.academy.domain.repositories.ExamRepository;
import tw.waterball.judgegirl.academy.domain.repositories.GroupRepository;
import tw.waterball.judgegirl.academy.domain.usecases.exam.*;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.primitives.Student;
import tw.waterball.judgegirl.primitives.exam.*;
import tw.waterball.judgegirl.primitives.problem.Language;
import tw.waterball.judgegirl.primitives.problem.Problem;
import tw.waterball.judgegirl.primitives.submission.Bag;
import tw.waterball.judgegirl.primitives.submission.Submission;
import tw.waterball.judgegirl.primitives.submission.events.VerdictIssuedEvent;
import tw.waterball.judgegirl.primitives.time.Duration;
import tw.waterball.judgegirl.problemapi.clients.FakeProblemServiceDriver;
import tw.waterball.judgegirl.problemapi.views.ProblemView;
import tw.waterball.judgegirl.springboot.academy.SpringBootAcademyApplication;
import tw.waterball.judgegirl.springboot.academy.amqp.VerdictIssuedEventListener;
import tw.waterball.judgegirl.springboot.academy.presenters.AnswerQuestionPresenter;
import tw.waterball.judgegirl.springboot.academy.view.*;
import tw.waterball.judgegirl.springboot.profiles.Profiles;
import tw.waterball.judgegirl.studentapi.clients.FakeStudentServiceDriver;
import tw.waterball.judgegirl.submissionapi.clients.EventPublisher;
import tw.waterball.judgegirl.submissionapi.clients.FakeSubmissionServiceDriver;
import tw.waterball.judgegirl.submissionapi.views.SubmissionView;
import tw.waterball.judgegirl.testkit.AbstractSpringBootTest;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static tw.waterball.judgegirl.academy.domain.usecases.exam.AnswerQuestionUseCase.BAG_KEY_EXAM_ID;
import static tw.waterball.judgegirl.commons.utils.DateUtils.*;
import static tw.waterball.judgegirl.commons.utils.MapUtils.map;
import static tw.waterball.judgegirl.commons.utils.StreamUtils.*;
import static tw.waterball.judgegirl.primitives.exam.Question.NO_QUOTA_LIMITATION;
import static tw.waterball.judgegirl.primitives.stubs.ProblemStubs.problemTemplate;
import static tw.waterball.judgegirl.primitives.stubs.SubmissionStubBuilder.randomizedSubmission;
import static tw.waterball.judgegirl.primitives.stubs.SubmissionStubBuilder.submission;
import static tw.waterball.judgegirl.primitives.time.DateProvider.now;
import static tw.waterball.judgegirl.primitives.time.Duration.during;
import static tw.waterball.judgegirl.problemapi.views.ProblemView.toEntity;
import static tw.waterball.judgegirl.problemapi.views.ProblemView.toViewModel;
import static tw.waterball.judgegirl.submissionapi.clients.SubmissionApiClient.CURRENTLY_ONLY_SUPPORT_C;
import static tw.waterball.judgegirl.submissionapi.clients.SubmissionApiClient.SUBMIT_CODE_MULTIPART_KEY_NAME;
import static tw.waterball.judgegirl.testkit.stubs.MultipartFileStubs.codes;

@ActiveProfiles({Profiles.JWT, Profiles.AMQP})
@ContextConfiguration(classes = SpringBootAcademyApplication.class)
class ExamControllerTest extends AbstractSpringBootTest {
    public static final int PROBLEM_ID = 2;
    public static final int ANOTHER_PROBLEM_ID = 300;

    public static final int NONEXISTING_EXAM_ID = 9999;
    public static final int NONEXISTING_PROBLEM_ID = 9999;
    public static final int STUDENT_A_ID = 1;
    public static final int STUDENT_B_ID = 1234;
    public static final int STUDENT_C_ID = 200;
    public static final int STUDENT_D_ID = 20000;
    public static final String LANG_ENV = Language.C.toString();
    public static final String SUBMISSION_ID = "SubmissionId";
    public static final String STUDENT_A_EMAIL = "studentA@example.com";
    public static final String STUDENT_B_EMAIL = "studentB@example.com";
    public static final String STUDENT_C_EMAIL = "studentC@example.com";
    public static final String STUDENT_D_EMAIL = "studentD@example.com";

    public static final Duration CLOSED_DURATION = during(beforeCurrentTime(2, HOURS), beforeCurrentTime(1, HOURS));
    public static final Duration ONGOING_DURATION = during(now(), afterCurrentTime(1, HOURS));
    public static final Duration UPCOMING_DURATION = during(afterCurrentTime(1, HOURS),
            afterCurrentTime(2, HOURS));

    @Autowired
    TokenService tokenService;
    @Autowired
    ExamRepository examRepository;
    @Autowired
    FakeProblemServiceDriver problemServiceDriver;
    @Autowired
    FakeStudentServiceDriver studentServiceDriver;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    VerdictIssuedEventListener verdictIssuedEventListener;
    @Autowired
    GroupRepository groupRepository;

    @Autowired
    FakeSubmissionServiceDriver submissionServiceDriver;

    private final int[] testcaseGrades = {20, 30, 50};
    private final int numOfTestcases = testcaseGrades.length;
    private ProblemView problem;
    private Submission submissionWith2ACs;
    private ProblemView anotherProblem;

    private final MockMultipartFile[] mockFiles = codes(SUBMIT_CODE_MULTIPART_KEY_NAME, 2);


    @BeforeEach
    void setup() {
        fakeProblemServiceDriver();
        fakeStudentServiceDriver();
    }

    private void fakeProblemServiceDriver() {
        Problem p1 = problemTemplate(testcaseGrades).id(PROBLEM_ID).title("Title_1").build(),
                p2 = problemTemplate(testcaseGrades).id(ANOTHER_PROBLEM_ID).title("Title_2").build();
        problem = toViewModel(p1);
        submissionWith2ACs = randomizedSubmission(p1, STUDENT_A_ID, 2);
        problemServiceDriver.addProblemView(problem);
        anotherProblem = toViewModel(p2);
        problemServiceDriver.addProblemView(anotherProblem);
    }

    private void fakeStudentServiceDriver() {
        asList(new Student(STUDENT_A_ID, "studentA", STUDENT_A_EMAIL, "passwordA"),
                new Student(STUDENT_B_ID, "studentB", STUDENT_B_EMAIL, "passwordB"),
                new Student(STUDENT_C_ID, "studentC", STUDENT_C_EMAIL, "passwordC"),
                new Student(STUDENT_D_ID, "studentD", STUDENT_D_EMAIL, "passwordD"))
                .forEach(studentServiceDriver::addStudent);
    }

    @AfterEach
    void cleanup() {
        examRepository.deleteAll();
        problemServiceDriver.clear();
        studentServiceDriver.clear();
    }

    @Test
    void WhenCreateExamWithEndTimeAfterStartTime_ShouldSucceed() throws Exception {
        String name = "test-contest", description = "problem statement";
        Date startTime = now(), endTime = now();
        Exam exam = new Exam(name, during(startTime, endTime), description);

        createExam(exam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").exists())
                .andExpect(jsonPath("name").value(name))
                .andExpect(jsonPath("startTime").value(startTime))
                .andExpect(jsonPath("endTime").value(endTime))
                .andExpect(jsonPath("description").value(description));
    }

    @Test
    void WhenUpdateExamWithExistingExamId_ShouldSucceed() throws Exception {
        Date firstTime = now();
        ExamView examView = createExamAndGet(firstTime, firstTime, "examTitle");
        Date secondTime = now();
        updateExam(new UpdateExamUseCase.Request(examView.getId(), "new name", secondTime, secondTime, "new problem statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(examView.getId()))
                .andExpect(jsonPath("name").value("new name"))
                .andExpect(jsonPath("startTime").value(secondTime))
                .andExpect(jsonPath("endTime").value(secondTime))
                .andExpect(jsonPath("description").value("new problem statement"));
    }

    @Test
    void WhenUpdateExamWithNonExistingExamId_ShouldRespondNotFound() throws Exception {
        Date firstTime = now();
        ExamView examView = createExamAndGet(firstTime, firstTime, "examTitle");
        Date secondTime = now();
        updateExam(new UpdateExamUseCase.Request(examView.getId() + 1, "new name", secondTime, secondTime, "new problem statement"))
                .andExpect(status().isNotFound());
    }

    @DisplayName("Given two groups G1(student: A) and G2(student: B, C), and an exam," +
            "When add the two groups as examinees, Then the examinees of the exam should be (A,B,C).")
    @Test
    void testAddTwoGroupsOfExamineesFrom() throws Exception {
        var exam = createExamAndGet(now(), now(), "Exam");
        Group G1 = givenGroupWithMembers("G1", STUDENT_A_ID);
        Group G2 = givenGroupWithMembers("G2", STUDENT_B_ID, STUDENT_C_ID);

        addGroupsOfExaminees(exam, G1, G2);

        var examinees = getExaminees(exam);
        shouldHaveExaminees(examinees, STUDENT_A_EMAIL, STUDENT_B_EMAIL, STUDENT_C_EMAIL);
    }

    private void shouldHaveExaminees(List<Student> actualExaminees, String... expectedExamineeEmails) {
        assertEquals(new HashSet<>(asList(expectedExamineeEmails)), mapToSet(actualExaminees, Student::getEmail));
    }

    @Test
    void WhenAddExaminees_A_B_Z_ShouldSucceedAndRespondErrorEmailList_Z() throws Exception {
        var exam = createExamAndGet(now(), now(), "Exam");

        List<String> errorEmails = getBody(
                addExaminees(exam.id, STUDENT_A_EMAIL, STUDENT_B_EMAIL, "studentZ@example.com")
                        .andExpect(status().isOk()), new TypeReference<>() {
                });

        assertEquals(1, errorEmails.size());
        assertEquals("studentZ@example.com", errorEmails.get(0));

        List<Student> examinees = getExaminees(exam);
        assertEquals(2, examinees.size());
    }

    @Test
    void WhenAddExamineesToNonExistingExam_ShouldRespondNotFound() throws Exception {
        addExaminees(1, STUDENT_A_EMAIL)
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenExaminees_A_B_C_WhenDeleteExaminees_A_C_ShouldOnlyRemainBInExam() throws Exception {
        var exam = createExamAndGet(now(), now(), "Exam");
        addExaminees(exam.id, STUDENT_A_EMAIL, STUDENT_B_EMAIL, "studentC@example.com")
                .andExpect(status().isOk());

        deleteExaminees(exam.getId(), STUDENT_A_EMAIL, "studentC@example.com");

        List<Student> examinees = getExaminees(exam);
        assertEquals(1, examinees.size(), "Should only remain B in the exam.");
        assertEquals(STUDENT_B_ID, examinees.get(0).getId(), "Should only remain B in the exam.");
    }

    @DisplayName("Given Student participates Exams A, B, C, D (only B, D are upcoming) " +
            "When get student's upcoming exams, Should respond B D")
    @Test
    void testFilterUpcomingStudentExams() {
        givenStudentParticipatingExams(STUDENT_A_ID,
                givenOngoingExams("A", "C"),
                givenUpcomingExams("B", "D"));

        List<ExamView> exams = getStudentExams(STUDENT_A_ID, ExamFilter.Status.upcoming);
        shouldRespondExams(exams, "B", "D");
    }

    @DisplayName("Given Student participates Exams A, B, C, D, E, F, G (only B, F, G are closed) " +
            "When get student's closed exams with skip=1, size=2, Should respond F, G")
    @Test
    void testFilterClosedStudentExamsWithPaging() {
        givenStudentParticipatingExams(STUDENT_A_ID,
                givenOngoingExams("A", "C", "D", "E"),
                givenClosedExams("B", "F", "G"));

        List<ExamView> exams = getStudentExams(STUDENT_A_ID, ExamFilter.Status.closed, 1, 2);
        shouldRespondExams(exams, "F", "G");
    }


    @DisplayName("Given Student participates Exams A, B, C, D, E, F, G, " +
            "When get all student's exams with skip=1, size=4, Should respond B, C, D, E, F")
    @Test
    void testGetAllStudentsExams() {
        givenStudentParticipatingExams(
                STUDENT_A_ID,
                givenOngoingExams("A", "B", "C"),
                givenUpcomingExams("D", "E"),
                givenClosedExams("F", "G"));

        List<ExamView> exams = getStudentExams(STUDENT_A_ID, ExamFilter.Status.all, 1, 5);
        shouldRespondExams(exams, "B", "C", "D", "E", "F");
    }


    @DisplayName("Given Student participates Exams A, B, C, D, E, F, G (only A, B, C, D are ongoing) " +
            "When get student's ongoing exams with skip=1, size=500000, Should respond B, C, D")
    @Test
    void testFilterOngoingStudentExamsWithPaging() {
        givenStudentParticipatingExams(STUDENT_A_ID,
                givenClosedExams("E", "F", "G"),
                givenOngoingExams("A", "B", "C", "D"));

        List<ExamView> exams = getStudentExams(STUDENT_A_ID, ExamFilter.Status.ongoing, 1, 50000);
        shouldRespondExams(exams, "B", "C", "D");
    }

    @Test
    void GivenOneExam_WhenCreateQuestionForExistingExam_ShouldRespondTheQuestionAndQuestionShouldBeAddedIntoExam() throws Exception {
        ExamView examView = createExamAndGet(now(), now(), "sample-exam");
        createQuestion(new CreateQuestionUseCase.Request(examView.getId(), PROBLEM_ID, 5, 100, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("examId").value(examView.id))
                .andExpect(jsonPath("problemId").value(PROBLEM_ID))
                .andExpect(jsonPath("quota").value(5))
                .andExpect(jsonPath("score").value(100));

        List<Question> questions = examRepository.findQuestionsInExam(examView.id);
        assertEquals(1, questions.size());
    }

    @Test
    void WhenCreateQuestionForNonExistingExam_ShouldRespondNotFound() throws Exception {
        createQuestion(new CreateQuestionUseCase.Request(NONEXISTING_EXAM_ID, PROBLEM_ID, 5, 100, 1))
                .andExpect(status().isNotFound());
    }

    @Test
    void WhenCreateQuestionForNonExistingProblem_ShouldRespondNotFound() throws Exception {
        var exam = createExamAndGet(now(), now(), "sample-exam");
        createQuestion(new CreateQuestionUseCase.Request(exam.getId(), NONEXISTING_PROBLEM_ID, 5, 100, 1))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void GivenOneExamAndOneQuestionCreated_WhenUpdateTheQuestion_ShouldSucceed() throws Exception {
        var exam = createExamAndGet(now(), now(), "sample-exam");
        createQuestion(new CreateQuestionUseCase.Request(exam.getId(), 2, 5, 100, 1));

        anotherTransaction(() ->
                updateQuestion(new UpdateQuestionUseCase.Request(exam.id, PROBLEM_ID, 6, 150, 2))
                        .andExpect(status().isOk()));

        anotherTransaction(() -> {
            Question question = examRepository.findById(exam.getId()).orElseThrow().getQuestions().get(0);
            assertEquals(6, question.getQuota());
            assertEquals(150, question.getScore());
            assertEquals(2, question.getQuestionOrder());
        });

    }

    @Test
    void GivenOneExamAndOneQuestionCreated_WhenDeleteTheQuestion_ShouldSucceed() throws Exception {
        ExamView examView = createExamAndGet(now(), now(), "sample-exam");
        createQuestion(new CreateQuestionUseCase.Request(examView.getId(), 2, 5, 100, 1));
        deleteQuestion(examView.getId(), PROBLEM_ID)
                .andExpect(status().isOk());
        assertEquals(0, getExamProgressOverview(examView.getId()).getQuestions().size());
    }

    @Test
    void WhenDeleteQuestionWithNonExistingExam_ShouldRespondNotFound() throws Exception {
        ExamView examView = createExamAndGet(now(), now(), "sample-exam");
        createQuestion(new CreateQuestionUseCase.Request(examView.getId(), 2, 5, 100, 1));
        deleteQuestion(NONEXISTING_EXAM_ID, PROBLEM_ID)
                .andExpect(status().isNotFound());
    }

    @Test
    void WhenDeleteQuestionWithNonExistingProblem_ShouldRespondNotFound() throws Exception {
        ExamView examView = createExamAndGet(now(), now(), "sample-exam");
        createQuestion(new CreateQuestionUseCase.Request(examView.getId(), 2, 5, 100, 1));
        deleteQuestion(examView.getId(), NONEXISTING_PROBLEM_ID)
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenExams_A_B_WhenGetAllExams_ShouldRespondExams_A_B() throws Exception {
        givenOngoingExams("A", "B");

        List<ExamView> exams = getAllExams();
        shouldRespondExams(exams, "A", "B");
    }

    @Test
    void GivenExams_A_B_C_D_E_WhenGetExamsWithSkip2Size2_ShouldRespondExams_C_D() throws Exception {
        givenOngoingExams("A", "B", "C", "D", "E");

        List<ExamView> exams = getExamsWithPaging(2, 2);
        shouldRespondExams(exams, "C", "D");
    }

    @Test
    void GivenExams_A_B_C_D_E_WhenGetExamsWithSkip2Size1000_ShouldRespondExams_C_D_E() throws Exception {
        givenOngoingExams("A", "B", "C", "D", "E");

        List<ExamView> exams = getExamsWithPaging(2, 1000);
        shouldRespondExams(exams, "C", "D", "E");
    }


    @DisplayName("Given exams A, B, C, D, E (A, C, D are upcoming), " +
            "When filter upcoming exams with skip=1, size=1, " +
            "Then should respond only C")
    @Test
    void testFilterUpcomingExams() throws Exception {
        givenOngoingExams("B", "E");
        givenUpcomingExams("A", "C", "D");

        List<ExamView> exams = getExamsWithPaging(ExamFilter.Status.upcoming, 1, 1);
        shouldRespondExams(exams, "C");
    }

    @DisplayName("Given exams A, B, C, D, E, F, G, H, I (A, B, G, H, I are closed), " +
            "When filter closed exams with skip=2, size=3, " +
            "Then should respond G, H, I")
    @Test
    void testFilterClosedExams() throws Exception {
        givenOngoingExams("C", "D", "E", "F");
        givenClosedExams("A", "B", "G", "H", "I");

        List<ExamView> exams = getExamsWithPaging(ExamFilter.Status.closed, 2, 3);
        shouldRespondExams(exams, "G", "H", "I");
    }

    @DisplayName("Given exams A, B, C, D, E, F, G, H, I (C, D, E, F are ongoing), " +
            "When filter ongoing exams with skip=2, size=9999, " +
            "Then should respond E, F")
    @Test
    void testFilterOngoingExams() throws Exception {
        givenClosedExams("A", "B", "G", "H", "I");
        givenOngoingExams("C", "D", "E", "F");

        List<ExamView> exams = getExamsWithPaging(ExamFilter.Status.ongoing, 2, 9999);
        shouldRespondExams(exams, "E", "F");
    }

    @Test
    void testGetExamById() {
        var exam = createExamAndGet(ONGOING_DURATION, "exam");
        var q1 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.id, PROBLEM_ID, 3, 50, 1));
        var q2 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.id, ANOTHER_PROBLEM_ID, 3, 50, 2));
        exam.questions.add(q1);
        exam.questions.add(q2);

        var actualExam = getExamById(exam.id);
        assertEquals(exam, actualExam);
    }

    @Test
    void GivenIAmNotAnExaminee_WhenAnswerExamQuestion_ShouldBeForbidden() throws Exception {
        ExamView currentExam = createExamAndGet(beforeCurrentTime(1, HOURS), afterCurrentTime(1, HOURS), "A");
        createQuestion(new CreateQuestionUseCase.Request(currentExam.getId(), PROBLEM_ID, NO_QUOTA_LIMITATION, 100, 1));

        answerQuestion(STUDENT_A_ID, currentExam)
                .andExpect(status().isForbidden());
    }

    @Test
    void GivenStudentParticipatingAClosedExamWithOneQuestion_WhenAnswerExamQuestion_ShouldFail() throws Exception {
        ExamView pastExam = createExamAndGet(beforeCurrentTime(2, HOURS), beforeCurrentTime(1, HOURS), "A");
        givenStudentParticipatingExam(STUDENT_A_ID, pastExam);
        createQuestion(new CreateQuestionUseCase.Request(pastExam.getId(), PROBLEM_ID, NO_QUOTA_LIMITATION, 100, 1));

        answerQuestion(STUDENT_A_ID, pastExam)
                .andExpect(status().is4xxClientError());
    }

    @Test
    void GivenStudentParticipatingAnUpcomingExamWithOneQuestion_WhenAnswerExamQuestion_ShouldFail() throws Exception {
        ExamView upcomingExam = createExamAndGet(afterCurrentTime(1, HOURS), afterCurrentTime(2, HOURS), "A");
        createQuestion(new CreateQuestionUseCase.Request(upcomingExam.getId(), PROBLEM_ID, NO_QUOTA_LIMITATION, 100, 1));
        givenStudentParticipatingExam(STUDENT_A_ID, upcomingExam);

        answerQuestion(STUDENT_A_ID, upcomingExam)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Timeout(5)
    void WheneverReceiveNewVerdict_ShouldUpdateBestRecordOfAQuestion() {
        var exam = createExamAndGet(beforeCurrentTime(1, HOURS), afterCurrentTime(1, HOURS), "A");
        givenStudentParticipatingExam(STUDENT_A_ID, exam);
        createQuestion(new CreateQuestionUseCase.Request(exam.id, PROBLEM_ID, NO_QUOTA_LIMITATION, 100, 1));

        publishVerdict(exam, submission("A").CE(100).build(STUDENT_A_ID, PROBLEM_ID, CURRENTLY_ONLY_SUPPORT_C));
        var bestRecord = awaitVerdictIssuedEventAndGetBestRecord(exam.id, PROBLEM_ID, STUDENT_A_ID);
        assertEquals(0, bestRecord.getGrade());

        publishVerdict(exam, submission("B").WA(10, 10).AC(10, 10, 20)
                .build(STUDENT_A_ID, PROBLEM_ID, CURRENTLY_ONLY_SUPPORT_C));
        bestRecord = awaitVerdictIssuedEventAndGetBestRecord(exam.id, PROBLEM_ID, STUDENT_A_ID);
        assertEquals(20, bestRecord.getGrade());

        publishVerdict(exam, submission("C").AC(50, 50, 10)
                .AC(10, 10, 20)
                .build(STUDENT_A_ID, PROBLEM_ID, CURRENTLY_ONLY_SUPPORT_C));
        bestRecord = awaitVerdictIssuedEventAndGetBestRecord(exam.id, PROBLEM_ID, STUDENT_A_ID);
        assertEquals(30, bestRecord.getGrade());

        publishVerdict(exam, submission("D").AC(25, 25, 10)
                .AC(10, 10, 20)
                .build(STUDENT_A_ID, PROBLEM_ID, CURRENTLY_ONLY_SUPPORT_C));
        bestRecord = awaitVerdictIssuedEventAndGetBestRecord(exam.id, PROBLEM_ID, STUDENT_A_ID);
        assertEquals(30, bestRecord.getGrade());
        assertEquals(25, bestRecord.getMaximumRuntime());
        assertEquals(25, bestRecord.getMaximumMemoryUsage());
    }

    // TODO: drunk code, need to be improved
    @Test
    @Timeout(5)
    void testGetStudentExamProgressOverview() throws Exception {
        final int QUOTA = 5;
        Date start = beforeCurrentTime(1, HOURS), end = afterCurrentTime(1, HOURS);
        ExamView exam = createExamAndGet(start, end, "sample-exam");
        var q1 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.getId(), PROBLEM_ID, QUOTA, 50, 1)).toEntity();
        var q2 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.getId(), ANOTHER_PROBLEM_ID, QUOTA, 50, 2)).toEntity();
        givenStudentParticipatingExam(STUDENT_A_ID, exam);
        answerQuestion(STUDENT_A_ID, exam).andExpect(status().isOk());
        publishVerdict(problem, exam, submissionWith2ACs);

        awaitVerdictIssuedEvent();
        ExamHome examHome = getExamProgressOverview(exam.getId());
        ExamHome.QuestionItem firstQuestion = examHome.getQuestionById(q1.getId()).orElseThrow();
        ExamHome.QuestionItem secondQuestion = examHome.getQuestionById(q2.getId()).orElseThrow();

        int expectedQ1Score = q1.calculateScore(submissionWith2ACs);
        assertEquals(exam.getId(), examHome.getId());
        assertEquals(exam.getName(), examHome.getName());
        assertEquals(start, examHome.getStartTime());
        assertEquals(end, examHome.getEndTime());
        assertEquals(exam.getDescription(), examHome.getDescription());
        assertEquals(2, examHome.getQuestions().size());
        assertEquals(expectedQ1Score, examHome.getTotalScore());

        assertEquals(QUOTA - 1, firstQuestion.getRemainingQuota());
        assertEquals(submissionWith2ACs.getGrade(), firstQuestion.getBestRecord().getScore());
        assertEquals(QUOTA, secondQuestion.getRemainingQuota());
        assertEquals(expectedQ1Score, firstQuestion.getYourScore());
        assertEquals(0, secondQuestion.getYourScore());
        assertEquals(firstQuestion.getProblemTitle(), problem.getTitle());
        assertEquals(secondQuestion.getProblemTitle(), anotherProblem.getTitle());
    }

    @Test
    void testGetExamOverview() {
        final int QUOTA = 5;
        Date start = beforeCurrentTime(1, HOURS), end = afterCurrentTime(1, HOURS);
        ExamView exam = createExamAndGet(start, end, "exam");
        QuestionView q1 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.getId(), PROBLEM_ID, QUOTA, 50, 1));
        QuestionView q2 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.getId(), ANOTHER_PROBLEM_ID, QUOTA, 50, 2));

        ExamOverview examOverview = getExamOverview(exam.getId());
        ExamOverview.QuestionItem firstQuestion = examOverview.getQuestionById(new Question.Id(q1.examId, q1.problemId)).orElseThrow();
        ExamOverview.QuestionItem secondQuestion = examOverview.getQuestionById(new Question.Id(q2.examId, q2.problemId)).orElseThrow();

        assertEquals(exam.getId(), examOverview.getId());
        assertEquals(exam.getName(), examOverview.getName());
        assertEquals(start, examOverview.getStartTime());
        assertEquals(end, examOverview.getEndTime());
        assertEquals(exam.getDescription(), examOverview.getDescription());
        assertEquals(2, examOverview.getQuestions().size());

        assertQuestionEquals(q1, firstQuestion, problem);
        assertQuestionEquals(q2, secondQuestion, anotherProblem);
    }

    @DisplayName("Give 8 students participating a ongoing exam, one question in the exam with submission quota = 3, " +
            "When 8 students answer that question 4 times at the same time, all should succeed in the first 3 times and fail in the 4th time.")
    @Test
    void testAnswerQuestionConcurrentlyWithSubmissionQuotas() {
        int SUBMISSION_QUOTA = 3;
        Integer[] studentIds = {333, 555, 5, 6, 7, 22, 56, 44};
        ExamView exam = createExamAndGet(beforeCurrentTime(1, HOURS), afterCurrentTime(1, HOURS), "A");
        givenStudentsParticipatingExam(studentIds, exam);
        createQuestion(new CreateQuestionUseCase.Request(exam.getId(), PROBLEM_ID, SUBMISSION_QUOTA, 100, 1));

        atTheSameTime(studentIds, studentId -> {
            for (int i = 0; i < SUBMISSION_QUOTA; i++) {
                var view = answerQuestionAndGet(exam, studentId);
                assertEquals(SUBMISSION_QUOTA - i - 1, view.getRemainingSubmissionQuota());
                shouldHaveSavedAnswer(view.getAnswer());
            }
            answerQuestion(studentId, exam).andExpect(status().is4xxClientError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("error").value(NoSubmissionQuotaException.NAME));
        });
    }

    @Test
    void GivenOneExamCreated_WhenDeleteTheExam_ShouldSucceed() {
        var exam = createExamAndGet(now(), now(), "exam");

        deleteExam(exam.id);

        assertTrue(examRepository.findById(exam.id).isEmpty());
    }

    @BeforeAll
    public static void initTest() throws SQLException {
        Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082")
                .start();
    }

    // TODO: drunk code, need to be improved
    @Test
    @Timeout(5)
    void testProduceExamTranscript() {
        Integer[] studentIds = {STUDENT_A_ID, STUDENT_B_ID, STUDENT_C_ID, STUDENT_D_ID};
        var exam = createExamAndGet(now(), oneSecondAfter(), "Exam");
        var q1 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.id, PROBLEM_ID, 1, 50, 0)).toEntity();
        var q2 = createQuestionAndGet(new CreateQuestionUseCase.Request(exam.id, ANOTHER_PROBLEM_ID, 1, 50, 1)).toEntity();
        givenStudentsParticipatingExam(studentIds, exam);
        var sA1 = publishVerdictAndAwait(problem, exam, randomizedSubmission(toEntity(problem), STUDENT_A_ID, 3));
        var sA2 = publishVerdictAndAwait(anotherProblem, exam, randomizedSubmission(toEntity(anotherProblem), STUDENT_A_ID, 3));
        var sB1 = publishVerdictAndAwait(problem, exam, randomizedSubmission(toEntity(problem), STUDENT_B_ID, 2));
        var sB2 = publishVerdictAndAwait(anotherProblem, exam, randomizedSubmission(toEntity(anotherProblem), STUDENT_B_ID, 1));
        var sC1 = publishVerdictAndAwait(problem, exam, submission(randomUUID().toString()).CE(problem.totalGrade).build(STUDENT_C_ID, PROBLEM_ID, CURRENTLY_ONLY_SUPPORT_C));
        var sC2 = publishVerdictAndAwait(anotherProblem, exam, randomizedSubmission(toEntity(anotherProblem), STUDENT_C_ID, 1));

        var transcript = produceExamTranscript(exam);

        var examineeRecordA = transcript.scoreBoard.get(STUDENT_A_EMAIL);
        var examineeRecordB = transcript.scoreBoard.get(STUDENT_B_EMAIL);
        var examineeRecordC = transcript.scoreBoard.get(STUDENT_C_EMAIL);
        int scoreA1 = q1.calculateScore(sA1), scoreA2 = q2.calculateScore(sA2);
        int scoreB1 = q1.calculateScore(sB1), scoreB2 = q2.calculateScore(sB2);
        int scoreC1 = q1.calculateScore(sC1), scoreC2 = q2.calculateScore(sC2);

        examineeShouldHaveScores(examineeRecordA, map(PROBLEM_ID, ANOTHER_PROBLEM_ID).to(scoreA1, scoreA2));
        examineeShouldHaveScores(examineeRecordB, map(PROBLEM_ID, ANOTHER_PROBLEM_ID).to(scoreB1, scoreB2));
        examineeShouldHaveScores(examineeRecordC, map(PROBLEM_ID, ANOTHER_PROBLEM_ID).to(scoreC1, scoreC2));
        assertFalse(transcript.scoreBoard.containsKey(STUDENT_D_EMAIL),
                "Student D doesn't participate the exam, should not include hos record.");

        assertEqualsIgnoreOrder(mapToList(asList(sA1, sA2, sB1, sB2, sC1, sC2), SubmissionView::toViewModel),
                mapToList(transcript.records, TranscriptView.RecordView::getSubmission));
    }

    @Test
    void testProduceCsvFileOfExamTranscript() throws Exception {
        Integer[] studentIds = {STUDENT_A_ID, STUDENT_B_ID, STUDENT_C_ID, STUDENT_D_ID};
        var exam = createExamAndGet(now(), oneSecondAfter(), "Exam");
        createQuestionAndGet(new CreateQuestionUseCase.Request(exam.id, PROBLEM_ID, 1, 50, 0)).toEntity();
        createQuestionAndGet(new CreateQuestionUseCase.Request(exam.id, ANOTHER_PROBLEM_ID, 1, 50, 1)).toEntity();
        givenStudentsParticipatingExam(studentIds, exam);
        publishVerdictAndAwait(problem, exam, randomizedSubmission(toEntity(problem), STUDENT_A_ID, 3));
        publishVerdictAndAwait(anotherProblem, exam, randomizedSubmission(toEntity(anotherProblem), STUDENT_A_ID, 3));
        publishVerdictAndAwait(problem, exam, randomizedSubmission(toEntity(problem), STUDENT_B_ID, 2));
        publishVerdictAndAwait(anotherProblem, exam, randomizedSubmission(toEntity(anotherProblem), STUDENT_B_ID, 1));
        publishVerdictAndAwait(problem, exam, submission(randomUUID().toString()).CE(problem.totalGrade).build(STUDENT_C_ID, PROBLEM_ID, CURRENTLY_ONLY_SUPPORT_C));
        publishVerdictAndAwait(anotherProblem, exam, randomizedSubmission(toEntity(anotherProblem), STUDENT_C_ID, 1));

        var transcript = produceExamTranscript(exam);

        var response = produceCsvFileExamTranscript(exam);

        final String COLUMN_NAME = "Name";
        final String COLUMN_EMAIL = "Email";
        final String COLUMN_PROBLEM_1_TITLE = problem.title;
        final String COLUMN_PROBLEM_2_TITLE = anotherProblem.title;
        final String COLUMN_TOTAL_SCORE = "Total Score";

        Reader in = new InputStreamReader(new ByteArrayInputStream(response.getContentAsByteArray()));
        var records = CSVFormat.DEFAULT.withHeader
                (COLUMN_NAME, COLUMN_EMAIL, COLUMN_PROBLEM_1_TITLE, COLUMN_PROBLEM_2_TITLE, COLUMN_TOTAL_SCORE)
                .parse(in);

        CSVRecord csvHeader = records.iterator().next();
        assertEquals(COLUMN_NAME, csvHeader.get(COLUMN_NAME));
        assertEquals(COLUMN_EMAIL, csvHeader.get(COLUMN_EMAIL));
        assertEquals(COLUMN_PROBLEM_1_TITLE, csvHeader.get(COLUMN_PROBLEM_1_TITLE));
        assertEquals(COLUMN_PROBLEM_2_TITLE, csvHeader.get(COLUMN_PROBLEM_2_TITLE));
        assertEquals(COLUMN_TOTAL_SCORE, csvHeader.get(COLUMN_TOTAL_SCORE));

        var scoreBoard = transcript.scoreBoard;
        for (CSVRecord record : records) {
            String email = record.get(COLUMN_EMAIL);
            assertTrue(scoreBoard.containsKey(email));

            var questionScores = scoreBoard.get(email).getQuestionScores();
            int problemScore = questionScores.get(problem.id);
            int anotherProblemScore = questionScores.get(anotherProblem.id);
            assertEquals(problemScore, Integer.valueOf(record.get(problem.title)));
            assertEquals(anotherProblemScore, Integer.valueOf(record.get(anotherProblem.title)));

            int actualTotalScore = problemScore + anotherProblemScore;
            assertEquals(actualTotalScore, Integer.valueOf(record.get(COLUMN_TOTAL_SCORE)));
        }
    }

    @SneakyThrows
    private TranscriptView produceExamTranscript(ExamView exam) {
        return getBody(mockMvc.perform(withAdminToken(
                get("/api/exams/{examId}/transcript", exam.getId())))
                .andExpect(status().isOk()), TranscriptView.class);
    }

    @SneakyThrows
    private MockHttpServletResponse produceCsvFileExamTranscript(ExamView exam) {
        return mockMvc.perform(withAdminToken(
                get("/api/exams/{examId}/transcript/csv", exam.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andReturn().getResponse();
    }

    @SneakyThrows
    AnswerQuestionPresenter.View answerQuestionAndGet(ExamView exam, Integer studentId) {
        return getBody(answerQuestion(studentId, exam).andExpect(status().isOk()),
                AnswerQuestionPresenter.View.class);
    }

    @SneakyThrows
    ResultActions answerQuestion(int studentId, ExamView exam) {
        return mockMvc.perform(withAdminToken(
                multipart("/api/exams/{examId}/problems/{problemId}/{langEnvName}/students/{studentId}/answers",
                        exam.getId(), problem.getId(), LANG_ENV, studentId)
                        .file(mockFiles[0])
                        .file(mockFiles[1])));
    }

    void examineeShouldHaveScores(TranscriptView.ExamineeRecordView examineeRecord, Map<Integer, Integer> problemIdToExpectedScore) {
        assertEquals(examineeRecord.getQuestionScores(), problemIdToExpectedScore);
    }

    void assertQuestionEquals(QuestionView expectedQuestion, ExamOverview.QuestionItem actualQuestion, ProblemView problem) {
        assertEquals(expectedQuestion.getExamId(), actualQuestion.getExamId());
        assertEquals(expectedQuestion.getProblemId(), actualQuestion.getProblemId());
        assertEquals(expectedQuestion.getQuota(), actualQuestion.getQuota());
        assertEquals(expectedQuestion.getScore(), actualQuestion.getMaxScore());
        assertEquals(expectedQuestion.getQuestionOrder(), actualQuestion.getQuestionOrder());
        assertEquals(problem.getTitle(), actualQuestion.getProblemTitle());
    }

    private void addGroupsOfExaminees(ExamView exam, Group... groups) throws Exception {
        mockMvc.perform(withAdminToken(
                post("/api/exams/{examId}/groups", exam.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddGroupOfExamineesUseCase.Request(exam.getId(), mapToList(groups, Group::getName))))))
                .andExpect(status().isOk());
    }

    private List<Student> getExaminees(ExamView exam) throws Exception {
        return getBody(mockMvc.perform(withAdminToken(
                get("/api/exams/{examId}/students", exam.id)))
                .andExpect(status().isOk()), new TypeReference<>() {
        });
    }

    private void publishVerdict(ExamView exam, Submission submission) {
        publishVerdict(problem, exam, submission);
    }


    private Submission publishVerdictAndAwait(ProblemView problem, ExamView exam, Submission submission) {
        submissionServiceDriver.add(submission);
        publishVerdict(problem, exam, submission);
        awaitVerdictIssuedEvent();
        return submission;
    }

    private Submission publishVerdict(ProblemView problem, ExamView exam, Submission submission) {
        assertEquals(problem.getId(), submission.getProblemId());
        eventPublisher.publish(new VerdictIssuedEvent(problem.getId(), problem.getTitle(), submission.getStudentId(), submission.getId(),
                submission.mayHaveVerdict().orElseThrow(),
                submission.getSubmissionTime(),
                new Bag(singletonMap(BAG_KEY_EXAM_ID, String.valueOf(exam.id)))));
        return submission;
    }

    private Record awaitVerdictIssuedEventAndGetBestRecord(int examId, int problemId, int studentId) {
        awaitVerdictIssuedEvent();
        return examRepository.findRecordOfQuestion(new Question.Id(examId, problemId), studentId).orElseThrow();
    }

    private void awaitVerdictIssuedEvent() {
        verdictIssuedEventListener.onHandlingCompletion$.doWait();
    }

    private void shouldHaveSavedAnswer(AnswerView answer) {
        examRepository.findAnswer(new Answer.Id(answer.number, new Question.Id(answer.examId, answer.problemId), answer.studentId))
                .ifPresentOrElse(a -> {
                            assertEquals(answer.examId, a.getExamId());
                            assertEquals(answer.problemId, a.getProblemId());
                            assertEquals(answer.studentId, a.getStudentId());
                            assertEquals(answer.number, a.getNumber());
                            assertEquals(answer.getAnswerTime(), a.getAnswerTime());
                            assertEquals(answer.getSubmissionId(), a.getSubmissionId());

                        },
                        () -> fail("The answer is not saved."));
    }

    private void shouldRespondExams(List<ExamView> actualExams, String... expectedNames) {
        assertEquals(expectedNames.length, actualExams.size());
        for (int i = 0; i < actualExams.size(); i++) {
            assertEquals(expectedNames[i], actualExams.get(i).name);
        }
    }

    private List<ExamView> getExamsWithPaging(ExamFilter.Status status, int skip, int size) throws Exception {
        return getBody(mockMvc.perform(
                withAdminToken(get("/api/exams")
                        .queryParam("status", String.valueOf(status))
                        .queryParam("skip", String.valueOf(skip))
                        .queryParam("size", String.valueOf(size)))), new TypeReference<>() {
        });
    }

    private List<ExamView> getExamsWithPaging(int skip, int size) throws Exception {
        return getBody(mockMvc.perform(
                withAdminToken(get("/api/exams")
                        .queryParam("skip", String.valueOf(skip))
                        .queryParam("size", String.valueOf(size)))), new TypeReference<>() {
        });
    }

    private List<ExamView> getAllExams() throws Exception {
        return getBody(mockMvc.perform(withAdminToken(
                get("/api/exams"))), new TypeReference<>() {
        });
    }

    private void givenStudentParticipatingExam(int studentId, ExamView exam) {
        givenStudentParticipatingExams(studentId, singletonList(exam));
    }

    private Group givenGroupWithMembers(String name, Integer... studentIds) {
        Group group = groupRepository.save(new Group(name));
        group.addMembers(mapToSet(studentIds, MemberId::new));
        return groupRepository.save(group);
    }

    private void givenStudentsParticipatingExam(Integer[] studentIds, ExamView exam) {
        stream(studentIds).forEach(studentId -> givenStudentParticipatingExams(studentId, singletonList(exam)));
    }

    @SafeVarargs
    private void givenStudentsParticipatingExams(Integer[] studentIds, List<ExamView>... exams) {
        stream(studentIds).forEach(studentId -> givenStudentParticipatingExams(studentId, exams));
    }

    @SafeVarargs
    private void givenStudentParticipatingExams(int studentId, List<ExamView>... examLists) {
        flatMapToList(examLists, List::stream)
                .forEach(exam -> addExaminee(exam.id, studentId));
    }

    private List<ExamView> givenClosedExams(String... names) {
        return givenExams(CLOSED_DURATION, names);
    }

    private List<ExamView> givenOngoingExams(String... names) {
        return givenExams(ONGOING_DURATION, names);
    }

    private List<ExamView> givenUpcomingExams(String... names) {
        return givenExams(UPCOMING_DURATION, names);
    }

    private List<ExamView> givenExams(Duration duration, String... names) {
        return mapToList(names, name -> createExamAndGet(duration, name));
    }

    private ExamView createExamAndGet(Date startTime, Date endTime, String name) {
        return createExamAndGet(during(startTime, endTime), name);
    }

    @SneakyThrows
    private ExamView createExamAndGet(Duration duration, String name) {
        return getBody(createExam(new Exam(name, duration, "description"))
                .andExpect(status().isOk()), ExamView.class);
    }

    @SneakyThrows
    private ResultActions createExam(Exam exam) {
        return mockMvc.perform(withAdminToken(
                post("/api/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateExamUseCase.Request(
                                exam.getName(), exam.getStartTime(), exam.getEndTime(), exam.getDescription())))));
    }

    @SneakyThrows
    private ResultActions updateExam(UpdateExamUseCase.Request request) {
        return mockMvc.perform(withAdminToken(
                put("/api/exams/{examId}", request.getExamId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))));
    }

    @SneakyThrows
    private List<ExamView> getStudentExams(int studentId, ExamFilter.Status status, int skip, int size) {
        return getBody(mockMvc.perform(
                withStudentToken(studentId, get("/api/students/{studentId}/exams", studentId)
                        .queryParam("status", String.valueOf(status))
                        .queryParam("skip", String.valueOf(skip))
                        .queryParam("size", String.valueOf(size))))
                .andExpect(status().isOk()), new TypeReference<>() {
        });
    }

    @SneakyThrows
    private List<ExamView> getStudentExams(int studentId, ExamFilter.Status status) {
        return getBody(mockMvc.perform(
                withStudentToken(studentId, get("/api/students/{studentId}/exams", studentId)
                        .queryParam("status", String.valueOf(status))))
                .andExpect(status().isOk()), new TypeReference<>() {
        });
    }

    @SneakyThrows
    private ExamView getExamById(int examId) {
        return getBody(mockMvc.perform(withAdminToken(
                get("/api/exams/{examId}", examId)))
                .andExpect(status().isOk()), ExamView.class);
    }

    @SneakyThrows
    private ExamHome getExamProgressOverview(int examId) {
        return getBody(mockMvc.perform(
                withStudentToken(STUDENT_A_ID,
                        get("/api/exams/{examId}/students/{studentId}/overview", examId, STUDENT_A_ID)))
                .andExpect(status().isOk()), ExamHome.class);
    }

    @SneakyThrows
    private ExamOverview getExamOverview(int examId) {
        return getBody(mockMvc.perform(withAdminToken(
                get("/api/exams/{examId}/overview", examId)))
                .andExpect(status().isOk()), ExamOverview.class);
    }

    private void addExaminee(int examId, int studentId) {
        examRepository.addExaminee(examId, studentId);
    }

    @SneakyThrows
    private ResultActions addExaminees(int examId, String... emails) {
        return mockMvc.perform(withAdminToken(
                post("/api/exams/{examId}/students", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(emails))));
    }

    @SneakyThrows
    private ResultActions deleteExaminees(int examId, String... emails) {
        return mockMvc.perform(withAdminToken(
                delete("/api/exams/{examId}/students", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(emails))));
    }

    @SneakyThrows
    private ResultActions createQuestion(CreateQuestionUseCase.Request request) {
        return mockMvc.perform(withAdminToken(
                post("/api/exams/{examId}/problems/{problemId}", request.getExamId(), request.getProblemId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))));
    }

    @SneakyThrows
    private QuestionView createQuestionAndGet(CreateQuestionUseCase.Request question) {
        return getBody(createQuestion(question)
                .andExpect(status().isOk()), QuestionView.class);
    }

    @SneakyThrows
    private ResultActions updateQuestion(UpdateQuestionUseCase.Request request) {
        return mockMvc.perform(withAdminToken(
                put("/api/exams/{examId}/problems/{problemId}", request.examId, request.problemId)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request))));
    }

    @SneakyThrows
    private ResultActions deleteQuestion(int examId, int problemId) {
        return mockMvc.perform(withAdminToken(
                delete("/api/exams/{examId}/problems/{problemId}", examId, problemId)));
    }

    @SneakyThrows
    private void deleteExam(int examId) {
        mockMvc.perform(withAdminToken(delete("/api/exams/{examId}", examId)))
                .andExpect(status().isOk());
    }

}