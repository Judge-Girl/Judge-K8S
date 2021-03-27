package tw.waterball.judgegirl.springboot.student.controllers.it;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tw.waterball.judgegirl.commons.exceptions.NotFoundException;
import tw.waterball.judgegirl.entities.Group;
import tw.waterball.judgegirl.entities.Student;
import tw.waterball.judgegirl.springboot.profiles.Profiles;
import tw.waterball.judgegirl.springboot.student.SpringBootStudentApplication;
import tw.waterball.judgegirl.springboot.student.view.GroupView;
import tw.waterball.judgegirl.springboot.student.view.StudentView;
import tw.waterball.judgegirl.studentservice.domain.repositories.GroupRepository;
import tw.waterball.judgegirl.studentservice.domain.repositories.StudentRepository;
import tw.waterball.judgegirl.studentservice.domain.usecases.CreateGroupUseCase;
import tw.waterball.judgegirl.testkit.AbstractSpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author - wally55077@gmail.com
 */
@Transactional
@ActiveProfiles(Profiles.JWT)
@ContextConfiguration(classes = SpringBootStudentApplication.class)
public class GroupControllerTest extends AbstractSpringBootTest {

    private static final String GROUP_NAME = "groupName";
    private static final String STUDENT_PATH = "/api/students";
    private static final String GROUP_PATH = "/api/groups";

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private GroupRepository groupRepository;

    @AfterEach
    void cleanUp() {
        groupRepository.deleteAll();
        studentRepository.deleteAll();
    }

    @Test
    public void WhenCreateGroupWithUniqueName_ShouldCreateSuccessfully() throws Exception {
        ResultActions resultActions = createGroup(GROUP_NAME)
                .andExpect(status().isOk());
        GroupView groupView = getBody(resultActions, GroupView.class);
        assertEquals(GROUP_NAME, groupView.name);
        assertTrue(groupRepository.existsByName(GROUP_NAME));
    }

    @Test
    public void GiveOneGroupCreated_WhenCreateGroupWithDuplicateName_ShouldRejectWithBadRequest() throws Exception {
        createGroup(GROUP_NAME);
        createGroup(GROUP_NAME).andExpect(status().isBadRequest());
    }

    @Test
    public void GiveOneGroupCreated_WhenGetGroupById_ShouldRespondGroup() throws Exception {
        GroupView group = createGroupAndGet(GROUP_NAME);
        ResultActions resultActions = getGroupById(group.id).andExpect(status().isOk());
        GroupView body = getBody(resultActions, GroupView.class);
        assertEquals(group, body);
    }

    @Test
    public void GiveTwoGroupsCreated_WhenGetAll_ShouldRespondTwoGroups() throws Exception {
        createGroup(GROUP_NAME + 1);
        createGroup(GROUP_NAME + 2);
        List<GroupView> body = getBody(getAllGroups().andExpect(status().isOk()), new TypeReference<>() {
        });
        assertEquals(2, body.size());
    }

    private ResultActions getAllGroups() throws Exception {
        return mockMvc.perform(get(GROUP_PATH));
    }

    private ResultActions createGroup(String groupName) throws Exception {
        CreateGroupUseCase.Request request = new CreateGroupUseCase.Request(groupName);
        return mockMvc.perform(post(GROUP_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)));
    }

    @Test
    public void WhenGetGroupByNonExistingGroupId_ShouldRespondNotFound() throws Exception {
        int nonExistingGroupId = 123123;
        getGroupById(nonExistingGroupId).andExpect(status().isNotFound());
    }

    @Test
    public void GiveOneGroupCreated_WhenDeleteGroupById_ShouldDeleteSuccessfully() throws Exception {
        GroupView group = createGroupAndGet(GROUP_NAME);
        int groupId = group.id;
        deleteGroupById(groupId).andExpect(status().isOk());
        getGroupById(groupId).andExpect(status().isNotFound());
    }

    private GroupView createGroupAndGet(String groupName) throws Exception {
        return getBody(createGroup(groupName), GroupView.class);
    }

    private ResultActions getGroupById(Integer id) throws Exception {
        return mockMvc.perform(get(GROUP_PATH + "/{groupId}", id));
    }

    @Test
    public void WhenDeleteGroupByNonExistingGroupId_ShouldRespondNotFound() throws Exception {
        int nonExistingGroupId = 123123;
        deleteGroupById(nonExistingGroupId)
                .andExpect(status().isNotFound());
    }

    private ResultActions deleteGroupById(Integer id) throws Exception {
        return mockMvc.perform(delete(GROUP_PATH + "/{groupId}", id));
    }

    @Test
    public void GiveOneGroupCreated_WhenAddTwoStudentsIntoTheGroup_ShouldAddSuccessfully() throws Exception {
        GroupView body = createGroupAndGet(GROUP_NAME);
        StudentView studentA = signUpAndGetStudent("A");
        StudentView studentB = signUpAndGetStudent("B");
        int groupId = body.id;
        addStudentIntoGroup(groupId, studentA.id);
        addStudentIntoGroup(groupId, studentB.id);
        Group group = groupRepository.findGroupById(groupId).orElseThrow(NotFoundException::new);
        assertEquals(2, group.getStudents().size());
    }

    @Test
    public void GiveOneStudentIntoCreatedGroup_WhenDeleteOneStudentFromTheGroup_ShouldDeleteSuccessfully() throws Exception {
        GroupView body = createGroupAndGet(GROUP_NAME);
        StudentView studentA = signUpAndGetStudent("A");
        int groupId = body.id;
        int studentId = studentA.id;
        addStudentIntoGroup(groupId, studentId);
        deleteStudentFromGroup(groupId, studentId);
        Group group = groupRepository.findGroupById(groupId).orElseThrow(NotFoundException::new);
        assertEquals(0, group.getStudents().size());
    }

    @Test
    @Transactional
    public void GiveOneStudentIntoCreatedGroup_WhenDeleteGroupById_ShouldDeleteSuccessfully() throws Exception {
        GroupView body = createGroupAndGet(GROUP_NAME);
        StudentView studentA = signUpAndGetStudent("A");
        int groupId = body.id;
        int studentId = studentA.id;
        addStudentIntoGroup(groupId, studentId);
        deleteGroupById(groupId);
        Student student = studentRepository.findStudentById(studentId).orElseThrow(NotFoundException::new);
        assertEquals(0, student.getGroups().size());
    }

    private StudentView signUpAndGetStudent(String sign) throws Exception {
        String name = "name" + sign;
        String email = "email" + sign + "@example.com";
        String password = "password" + sign;
        return getBody(signUp(name, email, password), StudentView.class);
    }

    private ResultActions signUp(String name, String email, String password) throws Exception {
        Student newStudent = new Student(name, email, password);
        return mockMvc.perform(post(STUDENT_PATH + "/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(newStudent)));
    }

    private ResultActions addStudentIntoGroup(int groupId, int studentId) throws Exception {
        return mockMvc.perform(post(GROUP_PATH + "/{groupId}/students/{studentId}", groupId, studentId));
    }

    private ResultActions deleteStudentFromGroup(int groupId, int studentId) throws Exception {
        return mockMvc.perform(delete(GROUP_PATH + "/{groupId}/students/{studentId}", groupId, studentId));
    }

}
