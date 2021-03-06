package tw.waterball.judgegirl.studentapi.clients;

import tw.waterball.judgegirl.primitives.Student;

import java.util.List;

public interface StudentServiceDriver {

    List<Student> getStudentsByIds(List<Integer> ids);

    List<Student> getStudentsByEmails(List<String> emails);
}
