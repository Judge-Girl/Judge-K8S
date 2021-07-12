package tw.waterball.judgegirl.springboot.academy.presenters;

import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import tw.waterball.judgegirl.academy.domain.usecases.exam.CreateExamTranscriptUseCase;
import tw.waterball.judgegirl.academy.domain.usecases.exam.CreateExamTranscriptUseCase.ExamineeRecord;
import tw.waterball.judgegirl.commons.models.files.FileResource;
import tw.waterball.judgegirl.commons.utils.functional.GetById;
import tw.waterball.judgegirl.primitives.exam.Exam;
import tw.waterball.judgegirl.problemapi.views.ProblemView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.List.of;
import static tw.waterball.judgegirl.commons.utils.StreamUtils.*;

/**
 * @author - c11037at@gmail.com (snowman)
 */
public class ExamTranscriptCsvFilePresenter implements CreateExamTranscriptUseCase.Presenter {
    private List<ProblemView> problems;
    private List<ExamineeRecord> examineeRecords;
    private static final String EXAM_TRANSCRIPT_FILE_NAME = "ExamTranscript.csv";
    private static final String COLUMN_NAME = "Name";
    private static final String COLUMN_EMAIL = "Email";
    private static final String COLUMN_TOTAL_SCORE = "Total Score";

    @Override
    public void showExam(Exam exam) {
    }

    @Override
    public void showRecords(List<ExamineeRecord> examineeRecords) {
        this.examineeRecords = examineeRecords;
    }

    @Override
    public void showProblems(List<ProblemView> problems) {
        this.problems = problems;
    }

    @SneakyThrows
    public FileResource present() {
        var out = new ByteArrayOutputStream();
        var csvPrinter = new CSVPrinter(new PrintWriter(out, false, UTF_8),
                CSVFormat.DEFAULT.withHeader(createExamTranscriptCsvHeader(problems)));

        var csvBody = createExamTranscriptCsvBody(examineeRecords);
        for (List<String> record : csvBody) {
            csvPrinter.printRecord(record);
        }
        csvPrinter.flush();

        var in = new ByteArrayInputStream(out.toByteArray());
        return new FileResource(EXAM_TRANSCRIPT_FILE_NAME, out.size(), in);
    }

    private String[] createExamTranscriptCsvHeader(List<ProblemView> problemViews) {
        List<String> csvHeader = new ArrayList<>();
        csvHeader.add(COLUMN_NAME);
        csvHeader.add(COLUMN_EMAIL);
        for (ProblemView problemView : problemViews) {
            csvHeader.add(problemView.title);
        }
        csvHeader.add(COLUMN_TOTAL_SCORE);
        return csvHeader.toArray(new String[0]);
    }

    private List<List<String>> createExamTranscriptCsvBody(List<ExamineeRecord> examineeRecords) {
        return mapToList(examineeRecords, this::examTranscriptCsvRows);
    }

    private List<String> examTranscriptCsvRows(ExamineeRecord examineeRecord) {
        return flatMapToList(of(examineeCsvBodyRows(examineeRecord),
                questionScoreCsvBodyRows(problems, examineeRecord)), Collection::stream);
    }

    private List<String> examineeCsvBodyRows(ExamineeRecord examineeRecord) {
        var examinee = examineeRecord.getExaminee();
        return of(examinee.getName(), examinee.getEmail());
    }

    private List<String> questionScoreCsvBodyRows(Collection<ProblemView> problems,
                                                  ExamineeRecord examineeRecord) {
        List<String> scoreRows = new ArrayList<>(1 + problems.size());
        var questionScores = questionScores(examineeRecord);
        int totalScore = 0;
        for (var problem : problems) {
            int score = questionScores.get(problem.id);
            scoreRows.add(String.valueOf(score));
            totalScore += score;
        }
        scoreRows.add(String.valueOf(totalScore));
        return scoreRows;
    }

    private GetById<Integer, Integer> questionScores(ExamineeRecord examineeRecord) {
        var questionRecords = examineeRecord.getQuestionRecords();
        var idToQuestionScores = toMap(questionRecords,
                questionRecord -> questionRecord.getQuestion().getProblemId(),
                CreateExamTranscriptUseCase.QuestionRecord::calculateScore);
        return idToQuestionScores::get;
    }
}