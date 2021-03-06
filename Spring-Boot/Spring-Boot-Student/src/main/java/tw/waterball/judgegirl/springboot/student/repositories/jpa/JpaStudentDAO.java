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

package tw.waterball.judgegirl.springboot.student.repositories.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @author chaoyulee chaoyu2330@gmail.com
 */
@Repository
public interface JpaStudentDAO extends JpaRepository<StudentData, Integer> {
    Optional<StudentData> findByEmailAndPassword(String email, String pwd);

    Optional<StudentData> findByEmail(String email);

    Optional<StudentData> findStudentById(Integer id);

    boolean existsByEmail(String email);

    Page<StudentData> findByAdmin(boolean admin, Pageable pageable);
    
    List<StudentData> findByEmailIn(Iterable<String> email);

    List<StudentData> findByIdIn(Iterable<Integer> id);
}
