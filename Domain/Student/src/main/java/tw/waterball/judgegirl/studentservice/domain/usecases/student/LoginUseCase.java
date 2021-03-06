/*
 * Copyright 2020 Johnny850807 (Waterball) 潘冠辰
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package tw.waterball.judgegirl.studentservice.domain.usecases.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.waterball.judgegirl.commons.exceptions.NotFoundException;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.primitives.Student;
import tw.waterball.judgegirl.studentservice.domain.exceptions.ForbiddenLoginException;
import tw.waterball.judgegirl.studentservice.domain.exceptions.StudentPasswordIncorrectException;
import tw.waterball.judgegirl.studentservice.domain.repositories.StudentRepository;
import tw.waterball.judgegirl.studentservice.password.PasswordEncoder;

import javax.inject.Named;

import static tw.waterball.judgegirl.commons.exceptions.NotFoundException.notFound;

/**
 * @author chaoyulee chaoyu2330@gmail.com
 */
@Named
@AllArgsConstructor
public class LoginUseCase {
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public void execute(Request request, Presenter presenter)
            throws NotFoundException, StudentPasswordIncorrectException {

        Student student = findStudentByEmail(request);
        validatePassword(request.password, student.getPassword());
        if (request.admin && !student.isAdmin()) {
            throw new ForbiddenLoginException("Student account can not login as admin");
        }
        presenter.loginSuccessfully(student);
    }

    private Student findStudentByEmail(Request request) {
        return studentRepository
                .findByEmail(request.email)
                .orElseThrow(() -> notFound(Student.class).identifiedBy("email", request.email));
    }

    private void validatePassword(String rawPassword, String encodedPassword) throws StudentPasswordIncorrectException {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new StudentPasswordIncorrectException();
        }
    }

    @Data
    @NoArgsConstructor
    public static class Request {
        public String email;
        public String password;
        public boolean admin;

        public Request(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public interface Presenter {
        void setToken(TokenService.Token token);

        void loginSuccessfully(Student student);
    }
}
