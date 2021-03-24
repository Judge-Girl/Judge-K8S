package tw.waterball.judgegirl.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tw.waterball.judgegirl.commons.utils.JSR380Utils;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Exam {

    private Integer id = null;

    @NotBlank
    private String name;

    @NotNull
    private Date startTime;

    @NotNull
    private Date endTime;

    @NotNull
    private String description;

    private List<Question> questions = new ArrayList<>();

    public Exam(String name, Date startTime, Date endTime, String description) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.description = description;
    }

    public void validate() {
        JSR380Utils.validate(this);
        if (startTime.after(endTime)) {
            throw new IllegalStateException();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Exam exam = (Exam) o;
        return Objects.equals(id, exam.id) &&
                name.equals(exam.name) &&
                startTime.equals(exam.startTime) &&
                endTime.equals(exam.endTime) &&
                description.equals(exam.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, startTime, endTime);
    }
}