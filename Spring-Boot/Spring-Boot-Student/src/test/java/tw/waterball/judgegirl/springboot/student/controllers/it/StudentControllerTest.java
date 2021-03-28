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

package tw.waterball.judgegirl.springboot.student.controllers.it;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.entities.Admin;
import tw.waterball.judgegirl.entities.Student;
import tw.waterball.judgegirl.springboot.profiles.Profiles;
import tw.waterball.judgegirl.springboot.student.SpringBootStudentApplication;
import tw.waterball.judgegirl.springboot.student.controllers.LoginResponse;
import tw.waterball.judgegirl.springboot.student.repositories.jpa.JpaStudentDataPort;
import tw.waterball.judgegirl.springboot.student.view.StudentView;
import tw.waterball.judgegirl.studentservice.domain.exceptions.StudentIdNotFoundException;
import tw.waterball.judgegirl.studentservice.domain.usecases.ChangePasswordUseCase;
import tw.waterball.judgegirl.studentservice.domain.usecases.SignInUseCase;
import tw.waterball.judgegirl.testkit.AbstractSpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tw.waterball.judgegirl.commons.utils.HttpHeaderUtils.bearerWithToken;
import static tw.waterball.judgegirl.springboot.student.view.StudentView.toViewModel;


/**
 * @author chaoyulee chaoyu2330@gmail.com
 */
@ActiveProfiles(Profiles.JWT)
@ContextConfiguration(classes = SpringBootStudentApplication.class)
public class StudentControllerTest extends AbstractSpringBootTest {
    private Student student;
    private Student admin;

    @Autowired
    private JpaStudentDataPort studentRepository;

    @Autowired
    private TokenService tokenService;

    @BeforeEach
    void setup() {
        student = new Student("name", "email@example.com", "password");
        admin = new Admin("adminName", "admin@example.com", "adminPassword");
    }

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
    }

    @Test
    void WhenStudentSignUpCorrectly_ShouldRespondStudentView() throws Exception {
        StudentView body = signUpAndGetResponseBody(student);
        student.setId(body.id);
        assertEquals(toViewModel(student), body);
    }

    @Test
    void WhenAdminSignUpCorrectly_ShouldRespondStudentView() throws Exception {
        StudentView body = signUpAndGetResponseBody(admin);
        admin.setId(body.id);
        assertEquals(toViewModel(admin), body);
    }

    @Test
    void WhenSignUpWithEmptyName_ShouldRespondBadRequest() throws Exception {
        signUp("", "email@example.com", "password")
                .andExpect(status().isBadRequest());
    }

    @Test
    void WhenSignUpWithEmptyPassword_ShouldRespondBadRequest() throws Exception {
        signUp("name", "email@example.com", "")
                .andExpect(status().isBadRequest());
    }

    @Test
    void WhenSignUpWithIncorrectEmail_ShouldRespondBadRequest() throws Exception {
        signUp("name", "email", "password")
                .andExpect(status().isBadRequest());
    }

    @Test
    void GivenOneStudentSignedUp_WhenSignUpWithExistingEmail_ShouldRespondBadRequest() throws Exception {
        signUp(student);
        student = new Student("name", "email@example.com", "password");
        mockMvc.perform(post("/api/students/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(student)))
                .andExpect(status().isBadRequest());
    }

    private StudentView signUpAndGetResponseBody(Student student) throws Exception {
        return getBody(signUp(student).andExpect(status().isOk()), StudentView.class);
    }

    private ResultActions signUp(Student student) throws Exception {
        return mockMvc.perform(post("/api/students/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(student)));
    }

    private ResultActions signUp(String name, String email, String password) throws Exception {
        Student newStudent = new Student(name, email, password);
        return mockMvc.perform(post("/api/students/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(newStudent)));
    }

    @Test
    void GivenOneStudentSignedUp_WhenStudentLoginCorrectly_ShouldRespondLoginResponseWithCorrectToken() throws Exception {
        StudentView studentView = signUpAndGetResponseBody(student);
        LoginResponse body = signInAndGetResponseBody(this.student.getEmail(), this.student.getPassword());

        testStudentSignUp(studentView, body);
    }

    @Test
    void GivenOneAdminSignedUp_WhenAdminLoginCorrectly_ShouldRespondLoginResponseWithCorrectToken() throws Exception {
        StudentView studentView = signUpAndGetResponseBody(admin);
        LoginResponse body = signInAndGetResponseBody(this.admin.getEmail(), this.admin.getPassword());

        testStudentSignUp(studentView, body);
    }

    private void testStudentSignUp(StudentView view, LoginResponse body) {
        assertEquals(view.id, body.id);
        assertEquals(view.email, body.email);
        TokenService.Token token = tokenService.parseAndValidate(body.token);
        assertEquals(view.id, token.getStudentId());
        assertEquals(view.isAdmin, body.isAdmin);
    }

    @Test
    void GivenOneStudentSignedUp_WhenLoginWithWrongPassword_ShouldRespondBadRequest() throws Exception {
        signUp(student);

        signIn(this.student.getEmail(), "wrongPassword")
                .andExpect(status().isBadRequest());
    }

    @Test
    void GivenOneStudentSignedUp_WhenLoginWithWrongEmail_ShouldRespondNotFound() throws Exception {
        signUp(student);

        signIn("worngEmail@example.com", this.student.getPassword())
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenOneStudentSignedUp_WhenLoginWithWrongEmailAndPassword_ShouldRespondNotFound() throws Exception {
        signUp(student);

        signIn("worngEmail@example.com", "wrongPassword")
                .andExpect(status().isNotFound());
    }

    private LoginResponse signInAndGetResponseBody(String email, String password) throws Exception {
        return getBody(signIn(email, password).andExpect(status().isOk()), LoginResponse.class);
    }

    private ResultActions signIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/students/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new SignInUseCase.Request(email, password))));
    }

    @Test
    void GivenOneStudentSignedUp_WhenGetStudentById_ShouldRespondStudentView() throws Exception {
        StudentView student = signUpAndGetResponseBody(this.student);
        LoginResponse loginResponse = signInAndGetResponseBody(this.student.getEmail(), this.student.getPassword());

        StudentView body = getBody(getStudentById(student.id, loginResponse.token)
                .andExpect(status().isOk()), StudentView.class);

        this.student.setId(body.id);
        assertEquals(toViewModel(this.student), body);
    }

    @Test
    void WhenGetStudentByNonExistingStudentId_ShouldRespondNotFound() throws Exception {
        int nonExistingStudentId = 123123;
        TokenService.Token token = tokenService.createToken(new TokenService.Identity(nonExistingStudentId));

        getStudentById(nonExistingStudentId, token.getToken())
                .andExpect(status().isNotFound());
    }

    private ResultActions getStudentById(Integer id, String tokenString) throws Exception {
        return mockMvc.perform(get("/api/students/" + id)
                .header("Authorization", bearerWithToken(tokenString)));
    }

    @Test
    void GivenOneStudentSignedUp_WhenAuth_ShouldRespondLoginResponseWithNewToken() throws Exception {
        signUp(student);
        LoginResponse loginResponse = signInAndGetResponseBody(student.getEmail(), student.getPassword());

        // we must delay certain seconds so that our token's expiry date
        // will increase enough to make differences in its produced token
        Thread.sleep(2000);

        LoginResponse authResponse = authAndGetResponseBody(loginResponse.token);

        assertNotEquals(loginResponse.token, authResponse.token, "The renewed token must be different from the original one.");
        assertNotEquals(loginResponse.expiryTime, authResponse.expiryTime);
        assertEquals(loginResponse.id, authResponse.id);
        assertEquals(loginResponse.email, authResponse.email);
    }

    @Test
    void WhenAuthWithNonExistingStudentToken_ShouldRespondUnauthorized() throws Exception {
        int nonExistingStudentId = 123123;
        TokenService.Token token = tokenService.createToken(new TokenService.Identity(nonExistingStudentId));
        auth(token.getToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void WhenAuthWithInvalidToken_ShouldRespondUnauthorized() throws Exception {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdHVkZW50SWQiOjEsImV4cCI6MTYxNTgzMDMwOH0.bI1j9-fCT0Ubd8ntuFstTo-UAXxopvGZLOFYwyAmnX8";
        auth(invalidToken).andExpect(status().isUnauthorized());
    }

    private LoginResponse authAndGetResponseBody(String tokenString) throws Exception {
        return getBody(mockMvc.perform(post("/api/students/auth")
                .header("Authorization", bearerWithToken(tokenString)))
                .andExpect(status().isOk()), LoginResponse.class);
    }

    private ResultActions auth(String tokenString) throws Exception {
        return mockMvc.perform(post("/api/students/auth")
                .header("Authorization", bearerWithToken(tokenString)));
    }

    @Test
    void GivenOneStudentSignedUp_WhenChangePasswordWithCorrectCurrentPassword_ShouldSucceed() throws Exception {
        signUp(student);
        LoginResponse body = signInAndGetResponseBody(student.getEmail(), student.getPassword());

        String newPassword = "newPassword";
        changePassword(student.getPassword(), newPassword, body.id, body.token).andExpect(status().isOk());

        Student student = studentRepository.findStudentById(body.id)
                .orElseThrow(StudentIdNotFoundException::new).toEntity();
        assertNotEquals(newPassword, student.getPassword());
    }

    @Test
    void GivenOneStudentSignedUp_WhenChangePasswordWithWrongCurrentPassword_ShouldRejectWithBadRequest() throws Exception {
        signUp(student);
        LoginResponse body = signInAndGetResponseBody(student.getEmail(), student.getPassword());

        String wrongPassword = "wrongPassword";
        String newPassword = "newPassword";
        changePassword(wrongPassword, newPassword, body.id, body.token).andExpect(status().isBadRequest());

        Student student = studentRepository.findStudentById(body.id)
                .orElseThrow(StudentIdNotFoundException::new).toEntity();
        assertEquals(student.getPassword(), student.getPassword());
    }

    private ResultActions changePassword(String password, String newPassword, int id, String token) throws Exception {
        ChangePasswordUseCase.Request request = new ChangePasswordUseCase.Request(id, password, newPassword);
        return mockMvc.perform(patch("/api/students/" + id + "/password")
                .header("Authorization", bearerWithToken(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)));
    }

    @Test
    void GivenTenStudentsSignedUp_WhenGetStudentsWithSkip2Size3_ShouldRespondCorrectly() throws Exception {
        signUpTenStudents();

        List<StudentView> students = getBody(
                mockMvc.perform(get("/api/students?skip=2&&size=3"))
                        .andExpect(status().isOk()), new TypeReference<>() {
                });

        assertEquals(3, students.size());
        assertEquals("name2", students.get(0).name);
        assertEquals("name3", students.get(1).name);
        assertEquals("name4", students.get(2).name);
    }

    private void signUpTenStudents() throws Exception {
        for (int i = 0; i < 10; i++) {
            String name = "name" + i;
            signUp(name, name + "@example.com", "password");
        }
    }
}
