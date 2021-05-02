package tw.waterball.judgegirl.springboot.exam.repositories;

import lombok.AllArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import tw.waterball.judgegirl.entities.exam.Group;
import tw.waterball.judgegirl.examservice.domain.repositories.GroupRepository;
import tw.waterball.judgegirl.springboot.exam.repositories.jpa.GroupDAO;
import tw.waterball.judgegirl.springboot.exam.repositories.jpa.GroupData;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static tw.waterball.judgegirl.commons.utils.StreamUtils.mapToSet;
import static tw.waterball.judgegirl.springboot.exam.repositories.jpa.GroupData.toData;

/**
 * @author wally55077@gmail.com
 * @author johnny850807@gmail.com (Waterball)
 */
@Component
@AllArgsConstructor
public class JpaGroupRepository implements GroupRepository {

    private final GroupDAO groupDAO;

    @Override
    public boolean existsByName(String name) {
        return groupDAO.existsByName(name);
    }

    @Override
    public Group save(Group group) {
        GroupData groupData = groupDAO.saveAndFlush(toData(group));
        group.setId(groupData.getId());
        return group;
    }

    @Override
    public Set<Group> getOwnGroups(int memberId) {
        return mapToSet(groupDAO.getGroupsOwnedByMember(memberId),
                GroupData::toEntity);
    }

    @Override
    public void deleteAll() {
        groupDAO.deleteAll();
    }

    @Override
    public Optional<Group> findGroupById(int groupId) {
        return groupDAO.findById(groupId)
                .map(GroupData::toEntity);
    }

    @Override
    public boolean existsById(int id) {
        return groupDAO.existsById(id);
    }

    @Override
    public List<Group> findAllGroups() {
        return groupDAO.findAll().stream()
                .map(GroupData::toEntity)
                .collect(toList());
    }

    @Override
    public void deleteGroupById(int groupId) {
        try {
            groupDAO.deleteById(groupId);
        } catch (EmptyResultDataAccessException ignored) {
        }
    }
}
