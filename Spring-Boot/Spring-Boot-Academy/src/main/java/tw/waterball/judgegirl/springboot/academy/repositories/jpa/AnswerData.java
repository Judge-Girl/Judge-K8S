package tw.waterball.judgegirl.springboot.academy.repositories.jpa;

import lombok.*;
import tw.waterball.judgegirl.primitives.exam.Answer;
import tw.waterball.judgegirl.primitives.exam.Question;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.IdClass;
import java.io.Serializable;
import java.util.Date;

@Builder
@Getter
@Setter
@Entity(name = "answers")
@AllArgsConstructor
@NoArgsConstructor
@IdClass(AnswerData.Id.class)
public class AnswerData {
    @javax.persistence.Id
    private Integer number;
    @javax.persistence.Id
    private int examId;
    @javax.persistence.Id
    private int problemId;
    @javax.persistence.Id
    private int studentId;
    private String submissionId;
    private Date answerTime;

    public AnswerData(AnswerData.Id id, String submissionId, Date answerTime) {
        this.number = id.number;
        this.examId = id.examId;
        this.problemId = id.problemId;
        this.studentId = id.studentId;
        this.submissionId = submissionId;
        this.answerTime = answerTime;
    }

    public Answer toEntity() {
        var answerId = new Answer.Id(number, new Question.Id(examId, problemId), studentId);
        return new Answer(answerId, submissionId, answerTime);
    }

    public static AnswerData toData(Answer answer) {
        return new AnswerData(new Id(answer.getId()), answer.getSubmissionId(), answer.getAnswerTime());
    }

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer number;
        private int examId;
        private int problemId;
        private int studentId;

        public Id(Answer.Id id) {
            this.number = id.getNumber();
            examId = id.getQuestionId().getExamId();
            problemId = id.getQuestionId().getProblemId();
            this.studentId = id.getStudentId();
        }
    }

}
