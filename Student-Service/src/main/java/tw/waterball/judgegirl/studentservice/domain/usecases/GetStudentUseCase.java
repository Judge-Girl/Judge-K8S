package tw.waterball.judgegirl.studentservice.domain.usecases;

import lombok.Value;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.entities.Student;
import tw.waterball.judgegirl.studentservice.domain.exceptions.StudentIdNotFoundException;
import tw.waterball.judgegirl.studentservice.domain.repositories.StudentRepository;

import javax.inject.Named;

/**
 * @author chaoyulee chaoyu2330@gmail.com
 */
@Named
public class GetStudentUseCase {
    private final StudentRepository studentRepository;
    private final TokenService tokenService;

    public GetStudentUseCase(StudentRepository studentRepository, TokenService tokenService) {
        this.studentRepository = studentRepository;
        this.tokenService = tokenService;
    }

    public void execute(Request request, Presenter presenter) {
        TokenService.Token token = tokenService.parseAndValidate(request.tokenString);
        if (token.getStudentId() == request.id) {
            presenter.setStudent(studentRepository
                    .findStudentById(request.id)
                    .orElseThrow(StudentIdNotFoundException::new));
        } else {
            throw new RuntimeException("Should not reach here.");
        }
    }

    @Value
    public static class Request {
        public Integer id;
        public String tokenString;
    }

    public interface Presenter {
        void setStudent(Student student);
    }
}