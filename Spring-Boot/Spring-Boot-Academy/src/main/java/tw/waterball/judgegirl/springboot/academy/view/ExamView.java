package tw.waterball.judgegirl.springboot.academy.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.waterball.judgegirl.primitives.exam.Exam;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExamView {
    public Integer id;
    public String name;
    public Date startTime;
    public Date endTime;
    public String description;
    public List<QuestionView> questions;

    public static ExamView toViewModel(Exam exam) {
        return ExamView.builder()
                .id(exam.getId())
                .name(exam.getName())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .description(exam.getDescription())
                .questions(exam.getQuestions().stream().map(QuestionView::toViewModel).collect(Collectors.toList()))
                .build();
    }
}
