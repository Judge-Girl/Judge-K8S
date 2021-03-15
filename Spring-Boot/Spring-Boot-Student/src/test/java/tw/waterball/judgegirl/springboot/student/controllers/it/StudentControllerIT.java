package tw.waterball.judgegirl.springboot.student.controllers.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.entities.Student;
import tw.waterball.judgegirl.springboot.profiles.Profiles;
import tw.waterball.judgegirl.springboot.student.SpringBootProblemApplication;
import tw.waterball.judgegirl.springboot.student.controllers.LoginResponse;
import tw.waterball.judgegirl.springboot.student.repositories.jpa.JpaStudentDataPort;
import tw.waterball.judgegirl.springboot.student.view.StudentView;
import tw.waterball.judgegirl.studentservice.domain.usecases.SignInUseCase;
import tw.waterball.judgegirl.testkit.AbstractSpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tw.waterball.judgegirl.springboot.student.view.StudentView.toViewModel;


/**
 * @author chaoyulee chaoyu2330@gmail.com
 */
@ActiveProfiles(Profiles.JWT)
@ContextConfiguration(classes = SpringBootProblemApplication.class)
public class StudentControllerIT extends AbstractSpringBootTest {
    private Student student;

    @Autowired
    private JpaStudentDataPort studentRepository;

    @Autowired
    private TokenService tokenService;

    @BeforeEach
    void setup() {
        student = new Student("name", "email@example.com", "password");
    }

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
    }


    private ResultActions signUp(Student student) throws Exception {
        return mockMvc.perform(post("/api/students/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(student)));
    }

    private StudentView signUpAndGetResponseBody(Student student) throws Exception {
        return getBody(signUp(student).andExpect(status().isOk()), StudentView.class);
    }

    private ResultActions signIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/students/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new SignInUseCase.Request(email, password))));
    }

    private LoginResponse signInAndGetResponseBody(String email, String password) throws Exception {
        return getBody(signIn(email, password).andExpect(status().isOk()), LoginResponse.class);
    }

    private ResultActions getStudent(Integer id, String tokenString) throws Exception {
        return mockMvc.perform(get("/api/students/" + id)
                .header("Authorization", "bearer " + tokenString));
    }

    private ResultActions auth(String tokenString) throws Exception {
        return mockMvc.perform(post("/api/students/auth")
                .header("Authorization", "bearer " + tokenString));
    }

    private LoginResponse authAndGetResponseBody(String tokenString) throws Exception {
        return getBody(mockMvc.perform(post("/api/students/auth")
                .header("Authorization", "bearer " + tokenString))
                .andExpect(status().isOk()), LoginResponse.class);
    }

    @Test
    void WhenSignUpCorrectly_ShouldReturnStudentView() throws Exception {
        StudentView body = signUpAndGetResponseBody(student);
        student.setId(body.getId());
        assertEquals(toViewModel(student), body);
    }

    @Test
    void GivenSignUpAccount_WhenLoginCorrectly_ShouldReturnLoginResponse() throws Exception {
        StudentView studentView = signUpAndGetResponseBody(student);
        LoginResponse body = signInAndGetResponseBody(this.student.getEmail(), this.student.getPassword());

        assertEquals(studentView.getId(), body.id);
        assertEquals(studentView.getEmail(), body.email);

        TokenService.Token token = tokenService.parseAndValidate(body.token);
        assertEquals(studentView.getId(), token.getStudentId());
    }

    @Test
    void GivenSignUpAccount_WhenLoginWithWrongPassword_ShouldReturn400() throws Exception {
        signUp(student);
        signIn(this.student.getEmail(), "wrongPassword")
                .andExpect(status().isBadRequest());
    }

    @Test
    void GivenSignUpAccount_WhenLoginWithWrongEmail_ShouldReturn404() throws Exception {
        signUp(student);
        signIn("worngEmail@example.com", this.student.getPassword())
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenSignUpAccount_WhenLoginWithWrongEmailAndPassword_ShouldReturn404() throws Exception {
        signUp(student);
        signIn("worngEmail@example.com", "wrongPassword")
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenExistedStudentId_WhenGetStudent_ShouldReturnStudentView() throws Exception {
        StudentView student = signUpAndGetResponseBody(this.student);
        LoginResponse loginResponse = signInAndGetResponseBody(this.student.getEmail(), this.student.getPassword());
        StudentView body = getBody(getStudent(student.getId(), loginResponse.token)
                .andExpect(status().isOk()), StudentView.class);

        this.student.setId(body.getId());
        assertEquals(toViewModel(this.student), body);
    }

    @Test
    void GivenNotExistedStudentIdAndValidToken_WhenGetStudentWith_ShouldReturn404() throws Exception {
        int notExistedStudentId = 123123;
        TokenService.Token token = tokenService.createToken(new TokenService.Identity(notExistedStudentId));
        getStudent(notExistedStudentId, token.getToken())
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenCorrectToken_WhenAuth_ShouldReturnLoginResponse() throws Exception {
        signUp(student);
        LoginResponse loginResponse = signInAndGetResponseBody(student.getEmail(), student.getPassword());
        LoginResponse authResponse = authAndGetResponseBody(loginResponse.token);

        assertEquals(loginResponse.token, authResponse.token);
        assertNotEquals(loginResponse.expiryTime, authResponse.expiryTime);
        assertEquals(loginResponse.id, authResponse.id);
        assertEquals(loginResponse.email, authResponse.email);
    }

    @Test
    void GivenNonExistedStudentToken_WhenAuth_ShouldReturn401() throws Exception {
        int notExistedStudentId = 123123;
        TokenService.Token token = tokenService.createToken(new TokenService.Identity(notExistedStudentId));
        auth(token.getToken()).andExpect(status().is(401));;
    }

    @Test
    void GivenInvalidToken_WhenAuth_ShouldReturn401() throws Exception {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdHVkZW50SWQiOjEsImV4cCI6MTYxNTgzMDMwOH0.bI1j9-fCT0Ubd8ntuFstTo-UAXxopvGZLOFYwyAmnX8";
        auth(invalidToken).andExpect(status().is(401));
    }

}