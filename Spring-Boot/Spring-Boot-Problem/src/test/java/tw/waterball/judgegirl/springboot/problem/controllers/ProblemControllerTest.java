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
package tw.waterball.judgegirl.springboot.problem.controllers;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.commons.token.TokenService.Identity;
import tw.waterball.judgegirl.commons.token.TokenService.Token;
import tw.waterball.judgegirl.commons.utils.DirectoryUtils;
import tw.waterball.judgegirl.primitives.problem.*;
import tw.waterball.judgegirl.problem.domain.repositories.ProblemRepository;
import tw.waterball.judgegirl.problem.domain.usecases.PatchProblemUseCase;
import tw.waterball.judgegirl.problem.domain.usecases.PatchProblemUseCase.LanguageEnvUpsert;
import tw.waterball.judgegirl.problem.domain.usecases.PatchProblemUseCase.TestcaseUpsert;
import tw.waterball.judgegirl.problemapi.views.*;
import tw.waterball.judgegirl.springboot.problem.SpringBootProblemApplication;
import tw.waterball.judgegirl.springboot.profiles.Profiles;
import tw.waterball.judgegirl.springboot.profiles.productions.Redis;
import tw.waterball.judgegirl.testkit.AbstractSpringBootTest;
import tw.waterball.judgegirl.testkit.semantics.WithHeader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.*;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.apache.commons.io.FileUtils.forceDelete;
import static org.testcontainers.shaded.org.apache.commons.io.IOUtils.resourceToByteArray;
import static tw.waterball.judgegirl.commons.utils.HttpHeaderUtils.bearerWithToken;
import static tw.waterball.judgegirl.commons.utils.ResourceUtils.getResourceAsStream;
import static tw.waterball.judgegirl.commons.utils.StreamUtils.*;
import static tw.waterball.judgegirl.commons.utils.ZipUtils.unzipToDestination;
import static tw.waterball.judgegirl.commons.utils.ZipUtils.zipFilesFromResources;
import static tw.waterball.judgegirl.primitives.problem.JudgePluginTag.Type.OUTPUT_MATCH_POLICY;
import static tw.waterball.judgegirl.primitives.stubs.ProblemStubs.languageEnvTemplate;
import static tw.waterball.judgegirl.primitives.stubs.ProblemStubs.problemTemplate;
import static tw.waterball.judgegirl.problem.domain.usecases.PatchProblemUseCase.TestcaseUpsert.upsert;
import static tw.waterball.judgegirl.problemapi.views.ProblemItem.toProblemItem;
import static tw.waterball.judgegirl.problemapi.views.ProblemView.toEntity;
import static tw.waterball.judgegirl.springboot.problem.controllers.ProblemController.*;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Testcontainers
@ActiveProfiles({Profiles.JWT, Profiles.EMBEDDED_MONGO, Profiles.REDIS})
@AutoConfigureDataMongo
@ContextConfiguration(classes = {SpringBootProblemApplication.class, ProblemControllerTest.RedisConfig.class})
public class ProblemControllerTest extends AbstractSpringBootTest {

    public static final String REDIS_IMAGE_NAME = "redis";
    public static final int REDIS_PORT = 6379;
    public static final int ADMIN_ID = 12345;
    public static final int STUDENT1_ID = 22;
    public static final String API_PREFIX = "/api/problems";

    @Container
    public static GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse(REDIS_IMAGE_NAME)).withExposedPorts(REDIS_PORT);

    @Autowired
    GridFsTemplate gridFsTemplate;
    @Autowired
    ProblemRepository problemRepository;
    @Autowired
    TokenService tokenService;

    private static byte[] expectedProvidedCodesZip;
    private static byte[] expectedTestcaseIOsZip;
    private Token adminToken;
    private Token student1Token;

    @Redis
    @Configuration
    public static class RedisConfig {

        @Bean
        @Primary
        public RedisConnectionFactory testRedisConnectionFactory() {
            String address = REDIS.getHost();
            int port = REDIS.getMappedPort(REDIS_PORT);
            var redisConfig = new RedisStandaloneConfiguration(address, port);
            return new LettuceConnectionFactory(redisConfig);
        }

    }

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        expectedProvidedCodesZip = resourceToByteArray("/providedCodes/providedCodes.zip");
        expectedTestcaseIOsZip = resourceToByteArray("/testcaseIos/io.zip");
    }

    @BeforeEach
    void setup() {
        adminToken = tokenService.createToken(Identity.admin(ADMIN_ID));
        student1Token = tokenService.createToken(Identity.student(STUDENT1_ID));
    }

    @AfterEach
    void clean() {
        problemRepository.deleteAll();
    }

    @Test
    void GivenProblemSaved_WhenGetProblemById_ShouldRespondThatProblem() throws Exception {
        Problem problem = givenOneProblemSaved();

        var actualProblem = getProblem(problem.getId());

        assertProblemEquals(toViewModel(problem), actualProblem);
    }

    @Test
    void GivenTagsSaved_WhenGetAllTags_ShouldRespondAllTags() throws Exception {
        List<String> tags = givenTagsSaved("tag1", "tag2", "tag3");

        mockMvc.perform(get(API_PREFIX + "/tags"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(toJson(tags)));
    }

    private List<String> givenTagsSaved(String... tags) {
        List<String> tagList = asList(tags);
        problemRepository.saveTags(tagList);
        return tagList;
    }

    @Test
    void GivenTaggedProblemsSaved_WhenGetProblemsThatMatchToTheTags_ShouldRespondThoseProblemItems() throws Exception {
        ProblemItem targetProblem1 = toProblemItem(
                givenProblemWithTags(1, "tag1", "tag2"));
        ProblemItem targetProblem2 = toProblemItem(
                givenProblemWithTags(2, "tag1", "tag2"));

        filterProblemsWithTagsShouldContain(adminToken, asList("tag1", "tag2"), asList(targetProblem1, targetProblem2));
        filterProblemsWithTagsShouldContain(adminToken, singletonList("tag1"), asList(targetProblem1, targetProblem2));
        filterProblemsWithTagsShouldContain(adminToken, singletonList("tag2"), asList(targetProblem1, targetProblem2));
    }

    @Test
    void GivenTaggedProblemsSaved_WhenGetProblemsThatDontMatchToTags_ShouldRespondEmptyArray() throws Exception {
        toProblemItem(givenProblemWithTags(1, "tag1", "tag2"));
        toProblemItem(givenProblemWithTags(2, "tag1", "tag2"));

        filterProblemsWithTagsShouldContain(adminToken, asList("tag1", "tag2", "tag3"), emptyList());
        filterProblemsWithTagsShouldContain(adminToken, singletonList("Non-existent-tag"), emptyList());
    }


    @Test
    void Given10ProblemsSaved_WhenGetAllProblems_ShouldRespondAll10Problems() throws Exception {
        List<Problem> problems = givenProblemsSaved(10);

        assertEquals(mapToList(problems, ProblemItem::toProblemItem),
                getProblemItems(withToken(adminToken)));
    }

    @Test
    void GivenOneProblemSaved_WhenGetProblemsWithoutPageSpecified_ShouldRespondOnlyThatProblem() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();

        assertEquals(toProblemItem(expectedProblem), getProblemItems(withToken(adminToken)).get(0));
    }

    @Test
    void testProblemsPagination() throws Exception {
        List<Problem> expectedProblems = givenProblemsSaved(200);

        // Strict pagination testing
        int page = 0;
        List<ProblemItem> actualAllProblemItems = new ArrayList<>();
        Set<ProblemItem> actualProblemItemsInPreviousPage = new HashSet<>();
        List<ProblemItem> actualProblemItems;

        do {
            actualProblemItems = getProblemItemsInPage(adminToken, page);
            actualAllProblemItems.addAll(actualProblemItems);

            assertTrue(actualProblemItems.stream().noneMatch(actualProblemItemsInPreviousPage::contains),
                    "Problem duplicated in different pages.");
            actualProblemItemsInPreviousPage = new HashSet<>(actualProblemItems);
            page++;
        } while (!actualProblemItems.isEmpty());

        for (int i = 0; i < expectedProblems.size(); i++) {
            assertEquals(expectedProblems.get(i).getId(), actualAllProblemItems.get(i).id);
            assertEquals(expectedProblems.get(i).getTitle(), actualAllProblemItems.get(i).title);
        }
    }

    @Test
    void WhenSaveProblemWithTitle_ProblemShouldBeSavedAndItsIdShouldBeResponded() throws Exception {
        String randomTitle = randomUUID().toString();
        int id = saveProblemWithTitle(randomTitle);

        assertEquals(randomTitle, getProblem(id).getTitle());
    }

    @Test
    void GivenOneProblemSaved_WhenPatchProblemWithNewTitle_TheProblemShouldHaveNewTitle() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();
        String newTitle = randomUUID().toString();

        expectedProblem.setTitle(newTitle);
        patchProblem(patch -> patch.title(newTitle));

        var actualProblem = getProblem(expectedProblem.getId());
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenOneProblemSaved_WhenPatchProblemWithDescription_TheProblemShouldHaveNewDescription() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();
        String newDescription = randomUUID().toString();

        expectedProblem.setDescription(newDescription);
        patchProblem(patch -> patch.description(newDescription));

        var actualProblem = getProblem(expectedProblem.getId());
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenOneProblemSaved_WhenPatchProblemWithMatchPluginTag_TheProblemShouldHaveNewMatchPluginTag() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();
        JudgePluginTag pluginMatchTag = new JudgePluginTag(OUTPUT_MATCH_POLICY, "Judge Girl", "Test", "1.0");

        expectedProblem.setOutputMatchPolicyPluginTag(pluginMatchTag);
        patchProblem(patch -> patch.matchPolicyPluginTag(new PatchProblemUseCase.JudgePluginTagItem(pluginMatchTag)));

        var actualProblem = getProblem(expectedProblem.getId());
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenOneProblemSaved_WhenPatchProblemWithFilterPluginTags_TheProblemShouldHaveNewFilterPluginTags() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();
        var filterPluginTags = new HashSet<>(
                generate(10, i -> new JudgePluginTag(
                        JudgePluginTag.Type.FILTER, "Judge Girl", format("Test %d", i), format("%d.0", i)))
        );

        expectedProblem.setFilterPluginTags(filterPluginTags);
        patchProblem(patch -> patch.filterPluginTags(mapToList(filterPluginTags, PatchProblemUseCase.JudgePluginTagItem::new)));

        var actualProblem = getProblem(expectedProblem.getId());
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenOneProblemSaved_WhenPatchTheProblemWithNewTags_ShouldHaveUpdatedNewTags() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();

        List<String> newTags = asList("newTagA", "newTagB");
        expectedProblem.setTags(newTags);
        patchProblem(patch -> patch.tags(newTags));

        var actualProblem = getProblem(expectedProblem.getId());
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenOneProblemSaved_WhenPatchTheProblemToBeVisible_ThenProblemShouldBeUpdatedVisible() throws Exception {
        Problem expectedProblem = givenOneProblemSaved();

        expectedProblem.setVisible(true);
        patchProblem(patch -> patch.visible(true));

        var actualProblem = getProblem(expectedProblem.getId());
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenProblemSavedWithOneTestcase_WhenUpdateTestcase_ThenShouldUpdateSuccessfully() throws Exception {
        int problemId = 1;
        Problem expectedProblem = givenProblemSavedWithOneTestcase(problemId,
                new Testcase("A", problemId, 1, 1, 1, 1, 100));

        var expectedTestcaseUpdate =
                upsert(expectedProblem.getTestcases().get(0), tc -> tc.setGrade(100));
        expectedProblem.upsertTestcase(expectedTestcaseUpdate.toValue());
        upsertTestCase(problemId, expectedTestcaseUpdate);

        var actualProblem = getProblem(problemId);
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);

    }

    @Test
    void GivenProblemSaved_WhenAddNewTestcase_ThenShouldAddSuccessfully() throws Exception {
        int problemId = 1;
        Problem expectedProblem = saveProblem(problemId);

        var expectedTestcaseName = "123456";
        TestcaseUpsert testcaseUpsert = new TestcaseUpsert(expectedTestcaseName, problemId, 100, 300, 300, -1, 500);
        expectedProblem.upsertTestcase(testcaseUpsert.toValue());
        upsertTestCase(problemId, testcaseUpsert);

        var actualProblem = getProblem(problemId);
        assertProblemEquals(toViewModel(expectedProblem), actualProblem);
    }

    @Test
    void GivenProblems_1_2_3_Saved_WhenGetProblemsByIds_1_2_3_ShouldRespondProblems1_2_3() throws Exception {
        saveProblems(1, 2, 3);

        var actualProblems = getProblems(withToken(adminToken), 1, 2, 3);

        problemsShouldHaveIds(actualProblems, 1, 2, 3);
    }

    @Test
    void Given_1_ProbplemSaved_WhenGetProblemsByIds_1_2_ShouldOnlyRespondTheProblem_1() throws Exception {
        saveProblems(1);

        var actualProblems = getProblems(withToken(adminToken), 1, 2);

        problemsShouldHaveIds(actualProblems, 1);
    }

    @Test
    void GivenOneProblemSaved_WhenArchiveIt_ShouldSucceed_AndThenDeleteIt_ThenItShouldBeDeletedAndCantBeFound() throws Exception {
        int problemId = 1;
        saveProblems(problemId);

        archiveOrDeleteProblem(problemId);

        assertTrue(problemRepository.findProblemById(problemId).orElseThrow().isArchived());

        archiveOrDeleteProblem(problemId);

        assertTrue(problemRepository.findProblemById(problemId).isEmpty());
    }

    @Test
    void GivenProblemsSaved_WhenArchiveProblemById_1_AndThenGetAllProblems_ShouldNotRespondProblem_1() throws Exception {
        givenProblemsSaved(10);

        archiveOrDeleteProblem(1);
        var problems = getProblemItems(withToken(adminToken));

        assertTrue(problems.stream().allMatch(problem -> problem.id != 1));
    }

    @Test
    void GivenOneProblemWithLangEnvC_WhenUpdateTheLangEnvC_ThenCShouldBeUpdated() throws Exception {
        int problemId = 1;
        Problem problem = saveProblem(problemId);
        LanguageEnv languageEnv = problem.getLanguageEnv(Language.C);

        var langEnvUpdate = upsertLanguageEnv(languageEnv, update -> update.setResourceSpecCpu(8));
        upsertLanguageEnv(problemId, langEnvUpdate);

        var actualProblem = getProblem(problemId);
        var actualLangEnv = actualProblem.getLanguageEnvs().get(0);
        var expectedLangEnv = langEnvUpdate.toValue();
        expectedLangEnv.setProvidedCodesFileId(languageEnv.getProvidedCodesFileId());
        assertEquals(toViewModel(expectedLangEnv), actualLangEnv);
    }

    @Test
    void GivenOneProblemWithLangEnvC_WhenUploadProblemWithNewJavaEnv_ThenProblemShouldHaveJavaEnv() throws Exception {
        int problemId = 1;
        saveProblems(problemId);

        var newJavaEnvUpdate = upsertLanguageEnv(Language.JAVA);
        upsertLanguageEnv(problemId, newJavaEnvUpdate);

        var problem = getProblem(problemId);
        var languageEnvs = problem.getLanguageEnvs();
        assertEquals(2, languageEnvs.size());
        var actualJavaEnv = problem.getLanguageEnv(Language.JAVA).orElseThrow();
        assertEquals(toViewModel(newJavaEnvUpdate.toValue()), actualJavaEnv);
    }

    @Test
    void GivenProblemAndThreeTestcaseSaved_WhenDeleteTestcaseById_ThenShouldDeleteSuccessfully() throws Exception {
        int problemId = 1;
        problemRepository.save(problemTemplate(3).id(problemId).build());
        var problem = getProblem(problemId);

        var expectDeletedTestcase = problem.getTestcases().get(0);
        deleteTestCase(problem.getId(), expectDeletedTestcase.getId());

        var actualTestcases = getProblem(problemId).getTestcases();
        assertEquals(problem.getTestcases().size() - 1, actualTestcases.size());
        assertTrue(findFirst(actualTestcases, actualTestcase -> actualTestcase.getId().equals(expectDeletedTestcase.getId())).isEmpty());
    }

    @Test
    void GivenOneProblemSaved_WhenUploadTwoProvidedCodes_ShouldRespondProvidedCodesFileId_AndIoFiles() throws Exception {
        Language language = Language.C;
        int problemId = 1;
        saveProblem(problemId);

        String providedCodesId = uploadProvidedCodesAndGetFileId(problemId, language, getProvidedCodes());

        assertNotNull(providedCodesId);
        var problem = getProblem(problemId);
        problemShouldHaveProvidedCodesId(problem, providedCodesId, language);
        assertTrue(existsFile(providedCodesId), "ProvidedCodes haven't been saved.");

        downloadProvidedCodesShouldRespondContent(problemId, language.toString(), providedCodesId,
                expectedProvidedCodesZip);
    }

    @DisplayName("Given one problem saved with a testcase, " +
            "When upload the testcase IO files, " +
            "should respond a IO FileId and the IO files should be zipped and saved. " +
            "When upload the testcase IO files again to the same testcase, " +
            "the firstly saved IO files should be replaced and removed.")
    @Test
    void testTestcaseIoFilesUploading() throws Exception {
        int problemId = 1;
        Testcase testcase = new Testcase("ID", "A", problemId, 1000, 1000, 1000, -1, 100);
        givenProblemSavedWithOneTestcase(problemId, testcase);

        String testcaseIosFileId = uploadTestcaseIosAndGetFileId(problemId, testcase.getId(), getTestcaseIOFiles());

        var problem = getProblem(problemId);
        problemShouldHaveTestcaseIoFileId(problem, testcase.getId(), testcaseIosFileId);
        filesShouldBeSaved("TestcaseIoFiles haven't been saved completely.",
                getAllTestcaseIoFileIds(toEntity(problem)));

        downloadTestcaseIOsShouldRespondContent(problemId, testcase.getId(), expectedTestcaseIOsZip);

        String testcaseIosFileId2 = uploadTestcaseIosAndGetFileId(problemId, testcase.getId(), getTestcaseIOFiles());

        assertNotEquals(testcaseIosFileId, testcaseIosFileId2, "The secondly uploaded file's ID should be different to the first one.");
        downloadTestcaseIOsShouldRespondContent(problemId, testcase.getId(), expectedTestcaseIOsZip);
        assertFalse(existsFile(testcaseIosFileId), "The old testcaseIosFile should be removed after the new one is uploaded.");
    }

    @Test
    void WhenUploadTwoProvidedCodesWithNonExistingProblemId_ShouldRespondNotFound() throws Exception {
        Language language = Language.C;
        int nonExistingProblemId = 123;
        uploadProvidedCodes(nonExistingProblemId, language, getProvidedCodes()).andExpect(status().isNotFound());
    }

    @Test
    void GivenOneProblemSavedWithoutLanguageEnv_WhenUploadProvidedCodes_ShouldRespondBadRequest() throws Exception {
        Language language = Language.C;
        int problemId = saveProblemWithTitle("problemTitle");

        uploadProvidedCodes(problemId, language, getProvidedCodes())
                .andExpect(status().isBadRequest());
    }

    @Test
    void GivenOneProblemSaved_WhenArchiveIt_AndThenDeleteIt_ThenProblemProvidedCodesAndTestcaseIOsShouldBeDeleted() throws Exception {
        int problemId = 1;
        Problem problem = saveProblem(problemId);

        archiveOrDeleteProblem(problemId);
        archiveOrDeleteProblem(problemId);

        problemProvidedCodesAndTestcaseIOsShouldBeDeleted(problem);
    }

    @Test
    void GivenThreeInvisibleProblemsSaved_WhenStudentGetProblemsByIds_ThenShouldRespondEmptyProblems() throws Exception {
        Integer[] problemIds = {1, 2, 3};
        saveProblems(problemIds);

        var problems = getProblems(withToken(student1Token), problemIds);

        assertTrue(problems.isEmpty());
    }

    @Test
    void GivenThreeInvisibleProblemsSaved_WhenStudentGetProblemsByTagsAndPage_ThenShouldRespondEmptyProblems() throws Exception {
        String[] tags = {"tag1", "tag2"};
        Integer[] problemIds = {1, 2, 3};
        saveProblems(problemIds);

        var problems = getProblemsByTagsAndPage(student1Token, 0, tags);

        assertTrue(problems.isEmpty());
    }

    @Test
    void GivenThreeInvisibleProblemsSaved_WhenGuestGetProblemsByIds_ThenShouldRespondEmptyProblems() throws Exception {
        Integer[] problemIds = {1, 2, 3};
        saveProblems(problemIds);

        var problems = getProblems(problemIds);

        assertTrue(problems.isEmpty());
    }

    @Test
    void GivenOneInvisibleProblemSaved_WhenGuestGetProblem_ThenShouldRespondNotFound() throws Exception {
        int problemId = 1;
        saveProblems(problemId);

        mockMvc.perform(get(API_PREFIX + "/{problemId}", problemId))
                .andExpect(status().isNotFound());
    }

    @Test
    void GivenOneProblemSavedAndArchived_WhenRestoreProblemById_ThenTheProblemShouldNotBeArchived() throws Exception {
        int problemId = 1;
        saveProblem(problemId);
        archiveOrDeleteProblem(problemId);
        assertTrue(getProblem(problemId).isArchived());

        restoreProblem(problemId);

        var actualProblem = getProblem(problemId);
        assertFalse(actualProblem.isArchived());
    }

    private void deleteTestCase(int problemId, String testCaseId) throws Exception {
        mockMvc.perform(withToken(adminToken,
                delete(API_PREFIX + "/{problemId}/testcases/{testcaseId}", problemId, testCaseId)))
                .andExpect(status().isOk());
    }

    private void downloadProvidedCodesShouldRespondContent(int problemId, String languageEnv, String providedCodesFileId,
                                                           byte[] expectedContent) throws Exception {
        byte[] actualContent = mockMvc.perform(withToken(adminToken,
                get(API_PREFIX + "/{problemId}/{languageEnv}/providedCodes/{providedCodesFileId}",
                        problemId, languageEnv, providedCodesFileId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        assertZipContentEquals(expectedContent, actualContent);
    }

    private void downloadTestcaseIOsShouldRespondContent(int problemId, String testcaseId, byte[] expectedContent) throws Exception {
        byte[] actualContent = mockMvc.perform(withToken(adminToken,
                get(API_PREFIX + "/{problemId}/testcases/{testcaseId}/io",
                        problemId, testcaseId))).andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        assertZipContentEquals(expectedContent, actualContent);
    }

    private void assertZipContentEquals(byte[] expectedZip, byte[] actualZip) throws IOException {
        Path actualTempDir = createTempDirectory("judgegirl");
        Path expectTempDir = createTempDirectory("judgegirl");
        unzipToDestination(new ByteArrayInputStream(actualZip), actualTempDir);
        unzipToDestination(new ByteArrayInputStream(expectedZip), expectTempDir);
        assertTrue(DirectoryUtils.contentEquals(actualTempDir, expectTempDir));
        forceDelete(actualTempDir.toFile());
        forceDelete(expectTempDir.toFile());
    }

    private Problem givenProblemSavedWithOneTestcase(int problemId, Testcase testcase) throws Exception {
        Problem problem = saveProblem(problemId);
        upsertTestCase(problemId, TestcaseUpsert.fromTestcase(testcase));
        problem.upsertTestcase(testcase);
        return problem;
    }

    private void archiveOrDeleteProblem(int problemId) throws Exception {
        mockMvc.perform(withToken(adminToken,
                delete(API_PREFIX + "/{problemId}", problemId)))
                .andExpect(status().isOk());
    }

    private void restoreProblem(int problemId) throws Exception {
        mockMvc.perform(withToken(adminToken,
                patch(API_PREFIX + "/{problemId}/restore", problemId)))
                .andExpect(status().isOk());
    }

    private void patchProblem(Consumer<PatchProblemUseCase.Request.RequestBuilder> patching) throws Exception {
        var requestBuilder = PatchProblemUseCase.Request.builder();
        patching.accept(requestBuilder);
        patchProblem(requestBuilder.build());
    }

    private void patchProblem(PatchProblemUseCase.Request request) throws Exception {
        mockMvc.perform(withToken(adminToken,
                patch(API_PREFIX + "/{problemId}", request.problemId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());
    }

    private Problem givenOneProblemSaved() {
        return givenProblemsSaved(1).get(0);
    }

    private void upsertTestCase(int problemId, TestcaseUpsert testcaseUpsert) throws Exception {
        mockMvc.perform(withToken(adminToken,
                put(API_PREFIX + "/{problemId}/testcases/{testcaseId}",
                        problemId, testcaseUpsert.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(testcaseUpsert))))
                .andExpect(status().isOk());
    }

    private void problemsShouldHaveIds(List<ProblemView> actualProblems, Integer... problemIds) {
        Set<Integer> idsSet = Set.of(problemIds);
        actualProblems.forEach(problem -> assertTrue(idsSet.contains(problem.getId())));
    }

    private ProblemView getProblem(int problemId) throws Exception {
        var request = withAdminToken(get(API_PREFIX + "/{problemId}", problemId));
        return getBody(mockMvc.perform(request).andExpect(status().isOk()), ProblemView.class);
    }

    private List<ProblemView> getProblems(WithHeader withHeader, Integer... problemIds) throws Exception {
        String ids = String.join(", ", mapToList(problemIds, String::valueOf));
        var request = get(API_PREFIX).queryParam("ids", ids);
        withHeader.decorate(request);
        return getBody(mockMvc.perform(request).andExpect(status().isOk()), new TypeReference<>() {
        });
    }

    private List<ProblemView> getProblems(Integer... problemIds) throws Exception {
        return getProblems(WithHeader.empty(), problemIds);
    }

    private void saveProblems(Integer... problemIds) {
        stream(problemIds).forEach(problemId -> {
            Problem problem = problemTemplate().id(problemId).build();
            byte[] providedCodesZip = zipFilesFromResources("/providedCodes/file1.c", "/providedCodes/file2.c");
            byte[] testcaseIOsZip = zipFilesFromResources("/testcaseIos/in/", "/testcaseIos/out/");
            problemRepository.save(problem, singletonMap(problem.getLanguageEnv(Language.C),
                    new ByteArrayInputStream(providedCodesZip)));
        });
    }

    private void assertProblemEquals(ProblemView expect, ProblemView actual) {
        expect.judgeFilterPluginTags = sortToList(expect.judgeFilterPluginTags, comparing(JudgePluginTagView::toString));
        actual.judgeFilterPluginTags = sortToList(actual.judgeFilterPluginTags, comparing(JudgePluginTagView::toString));
        expect.testcases = sortToList(expect.testcases, comparing(TestcaseView::getId));
        actual.testcases = sortToList(actual.testcases, comparing(TestcaseView::getId));
        expect.tags = sortToList(expect.tags);
        actual.tags = sortToList(actual.tags);
        expect.languageEnvs = sortToList(expect.languageEnvs, comparing(LanguageEnvView::getLanguage));
        actual.languageEnvs = sortToList(actual.languageEnvs, comparing(LanguageEnvView::getLanguage));
        assertEquals(expect, actual);
    }

    private LanguageEnvUpsert upsertLanguageEnv(Language language) {
        return LanguageEnvUpsert.fromLangEnv(languageEnvTemplate(language).build());
    }

    private LanguageEnvUpsert upsertLanguageEnv(LanguageEnv languageEnv, Consumer<LanguageEnvUpsert> update) {
        return LanguageEnvUpsert.upsert(languageEnv, update);
    }

    private LanguageEnvUpsert upsertLanguageEnv(Language language, Consumer<LanguageEnvUpsert> update) {
        return LanguageEnvUpsert.upsert(languageEnvTemplate(language).build(), update);
    }

    private void upsertLanguageEnv(Integer problemId, LanguageEnvUpsert languageEnv) throws Exception {
        mockMvc.perform(withToken(adminToken,
                put(API_PREFIX + "/{problemId}/langEnv/{langEnv}",
                        problemId, languageEnv.getLanguage())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(languageEnv))))
                .andExpect(status().isOk());
    }

    private Problem saveProblem(int problemId) {
        saveProblems(problemId);
        return problemRepository.findProblemById(problemId).orElseThrow();
    }

    private int saveProblemWithTitle(String title) throws Exception {
        return parseInt(getContentAsString(
                mockMvc.perform(withToken(adminToken,
                        post(API_PREFIX)
                                .contentType(MediaType.TEXT_PLAIN_VALUE).content(title)))
                        .andExpect(status().isOk())));
    }

    //TODO
    private Problem givenProblemSavedWithProvidedCodesAndTestcaseIOs() {
        Problem problem = problemTemplate().build();
        return problemRepository.save(problem,
                singletonMap(problem.getLanguageEnv(Language.C), new ByteArrayInputStream(expectedProvidedCodesZip))
        );
    }

    private List<ProblemItem> getProblemItems(WithHeader withHeader) throws Exception {
        var request = get(API_PREFIX);
        withHeader.decorate(request);
        return getBody(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)), new TypeReference<>() {
        });
    }

    private List<ProblemItem> getProblemItemsInPage(Token token, int page) throws Exception {
        return getBody(mockMvc.perform(withToken(token, get(API_PREFIX)
                .queryParam("page", String.valueOf(page))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)), new TypeReference<>() {
        });
    }

    private List<Problem> givenProblemsSaved(int count) {
        Random random = new Random();
        var problems = range(0, count)
                .mapToObj((id) ->
                        problemTemplate().id(id)
                                .title(String.valueOf(random.nextInt())).build())
                .collect(toList());
        problems.forEach(problemRepository::save);
        return problems;
    }

    private Problem givenProblemWithTags(int problemId, String... tags) {
        Problem targetProblem = problemTemplate().id(problemId).tags(asList(tags)).build();
        return problemRepository.save(targetProblem);
    }

    private void filterProblemsWithTagsShouldContain(Token token, List<String> tags, List<ProblemItem> problemItems) throws Exception {
        String tagsSplitByCommas = String.join(", ", tags);
        mockMvc.perform(get(API_PREFIX)
                .header("Authorization", bearerWithToken(token.getToken()))
                .queryParam("tags", tagsSplitByCommas))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(problemItems)));
    }


    private List<ProblemView> getProblemsByTagsAndPage(Token token, int page, String... tags) throws Exception {
        String tagsSplitByCommas = String.join(",", tags);
        return getBody(mockMvc.perform(get(API_PREFIX)
                .header("Authorization", bearerWithToken(token.getToken()))
                .queryParam("tags", tagsSplitByCommas)
                .queryParam("page", String.valueOf(page)))
                .andExpect(status().isOk()), new TypeReference<>() {
        });
    }

    private MockMultipartFile[] getProvidedCodes() throws IOException {
        return new MockMultipartFile[]{
                new MockMultipartFile(PROVIDED_CODE_MULTIPART_KEY_NAME, "file1.c", "text/plain",
                        getResourceAsStream("/providedCodes/file1.c")),
                new MockMultipartFile(PROVIDED_CODE_MULTIPART_KEY_NAME, "file2.c", "text/plain",
                        getResourceAsStream("/providedCodes/file2.c"))
        };
    }

    private String uploadProvidedCodesAndGetFileId(int problemId, Language language, MockMultipartFile... files) throws Exception {
        return getContentAsString(uploadProvidedCodes(problemId, language, files)
                .andExpect(status().isOk()));
    }

    private ResultActions uploadProvidedCodes(int problemId, Language language, MockMultipartFile[] files) throws Exception {
        return mockMvc.perform(multipartRequestWithFiles("PUT",
                multipart(API_PREFIX + "/{problemId}/{langEnvName}/providedCodes", problemId, language.toString()),
                files));
    }

    private MockHttpServletRequestBuilder multipartRequestWithFiles(String httpMethod, MockMultipartHttpServletRequestBuilder requestBuilder,
                                                                    MockMultipartFile... files) {
        requestBuilder.with(request -> {
            request.setMethod(httpMethod);
            return request;
        });

        for (MockMultipartFile file : files) {
            requestBuilder = requestBuilder.file(file);
        }
        return withToken(adminToken, requestBuilder);
    }

    private String uploadTestcaseIosAndGetFileId(int problemId, String testcaseId, MockMultipartFile... files) throws Exception {
        String fileId = getContentAsString(mockMvc.perform(
                multipartRequestWithFiles("PUT",
                        multipart(API_PREFIX + "/{problemId}/testcases/{testcaseId}/io", problemId, testcaseId),
                        files)));
        assertFalse(fileId.isBlank(), " The fileId responded should not be empty or blank.");
        return fileId;
    }

    private MockMultipartFile[] getTestcaseIOFiles() throws IOException {
        return new MockMultipartFile[]{
                new MockMultipartFile(TESTCASE_STDIN_MULTIPART_KEY_NAME, "I1.in", "text/plain",
                        getResourceAsStream("/testcaseIos/in/I1.in")),
                new MockMultipartFile(TESTCASE_STDOUT_MULTIPART_KEY_NAME, "O1.out", "text/plain",
                        getResourceAsStream("/testcaseIos/out/O1.out")),
                new MockMultipartFile(TESTCASE_IN_FILES_MULTIPART_KEY_NAME, "I2.in", "text/plain",
                        getResourceAsStream("/testcaseIos/in/I2.in")),
                new MockMultipartFile(TESTCASE_IN_FILES_MULTIPART_KEY_NAME, "I3.in", "text/plain",
                        getResourceAsStream("/testcaseIos/in/I3.in")),
                new MockMultipartFile(TESTCASE_OUT_FILES_MULTIPART_KEY_NAME, "O2.out", "text/plain",
                        getResourceAsStream("/testcaseIos/out/O2.out")),
                new MockMultipartFile(TESTCASE_OUT_FILES_MULTIPART_KEY_NAME, "O3.out", "text/plain",
                        getResourceAsStream("/testcaseIos/out/O3.out")),
        };
    }

    private void problemShouldHaveProvidedCodesId(ProblemView problem, String fileId, Language language) {
        var langEnv = findFirst(problem.languageEnvs, lg -> lg.getLanguage().equals(language)).orElseThrow();
        assertEquals(fileId, langEnv.getProvidedCodesFileId());
    }

    private void problemShouldHaveTestcaseIoFileId(ProblemView problem, String testcaseId, String testcaseIoId) {
        var testcase = findFirst(problem.getTestcases(), t -> t.getId().equals(testcaseId)).orElseThrow();
        assertEquals(testcaseIoId, testcase.getIoFileId());
    }

    private void problemProvidedCodesAndTestcaseIOsShouldBeDeleted(Problem problem) {
        List<String> providedCodes = mapToList(problem.getLanguageEnvs().values(), LanguageEnv::getProvidedCodesFileId);
        List<String> fileIds = new LinkedList<>(providedCodes);
        fileIds.addAll(getAllTestcaseIoFileIds(problem));
        fileIds.forEach(fileId -> assertFalse(existsFile(fileId)));
    }

    private List<String> getAllTestcaseIoFileIds(Problem problem) {
        return problem.getTestcases().stream()
                .flatMap(testcase -> testcase.getTestcaseIO().stream())
                .map(TestcaseIO::getId).collect(toList());
    }

    private void filesShouldBeSaved(String message, List<String> fileIds) {
        for (String fileId : fileIds) {
            assertTrue(existsFile(fileId), message);
        }
    }

    private boolean existsFile(String fileId) {
        return ofNullable(gridFsTemplate.findOne(new Query(where("_id").is(fileId))))
                .map(gridFsTemplate::getResource)
                .map(GridFsResource::exists)
                .orElse(false);
    }

    private ProblemView toViewModel(Problem problem) {
        return ProblemView.toViewModel(problem);
    }

    private LanguageEnvView toViewModel(LanguageEnv languageEnv) {
        return LanguageEnvView.toViewModel(languageEnv);
    }

}

