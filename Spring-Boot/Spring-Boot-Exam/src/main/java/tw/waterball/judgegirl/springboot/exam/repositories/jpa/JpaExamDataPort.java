package tw.waterball.judgegirl.springboot.exam.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JpaExamDataPort extends JpaRepository<ExamData, Integer> {

    Optional<ExamData> findById(int examId);

    List<ExamData> findByIdIn(Collection<Integer> examIds);

}
