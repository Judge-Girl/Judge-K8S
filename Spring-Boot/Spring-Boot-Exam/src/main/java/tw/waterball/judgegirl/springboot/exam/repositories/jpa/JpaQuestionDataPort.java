package tw.waterball.judgegirl.springboot.exam.repositories.jpa;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaQuestionDataPort extends CrudRepository<QuestionData, Integer> {
    long deleteByIdAndExamId(int questionId, int examId);
}
