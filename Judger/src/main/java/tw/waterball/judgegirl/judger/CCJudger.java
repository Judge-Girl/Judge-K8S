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

package tw.waterball.judgegirl.judger;

import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tw.waterball.judgegirl.commons.models.files.FileResource;
import tw.waterball.judgegirl.commons.utils.ZipUtils;
import tw.waterball.judgegirl.entities.problem.Problem;
import tw.waterball.judgegirl.entities.problem.SubmittedCodeSpec;
import tw.waterball.judgegirl.entities.problem.Testcase;
import tw.waterball.judgegirl.entities.submission.Submission;
import tw.waterball.judgegirl.entities.submission.Verdict;
import tw.waterball.judgegirl.judger.infra.compile.CompileResult;
import tw.waterball.judgegirl.judger.infra.compile.Compiler;
import tw.waterball.judgegirl.judger.infra.compile.CompilerFactory;
import tw.waterball.judgegirl.judger.infra.testexecutor.TestcaseExecutionResult;
import tw.waterball.judgegirl.judger.infra.testexecutor.TestcaseExecutor;
import tw.waterball.judgegirl.judger.infra.testexecutor.TestcaseExecutorFactory;
import tw.waterball.judgegirl.judger.layout.*;
import tw.waterball.judgegirl.plugins.api.JudgeGirlPluginLocator;
import tw.waterball.judgegirl.problemapi.clients.ProblemServiceDriver;
import tw.waterball.judgegirl.problemapi.views.ProblemView;
import tw.waterball.judgegirl.submissionapi.clients.SubmissionServiceDriver;
import tw.waterball.judgegirl.submissionapi.clients.VerdictPublisher;
import tw.waterball.judgegirl.submissionapi.views.ReportView;
import tw.waterball.judgegirl.submissionapi.views.SubmissionView;
import tw.waterball.judgegirl.submissionapi.views.VerdictIssuedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@SuppressWarnings("WeakerAccess")
public class CCJudger extends PluginExtendedJudger {
    private final static Logger logger = LogManager.getLogger(CCJudger.class);
    private JudgerWorkspace judgerWorkspace;
    private ProblemServiceDriver problemServiceDriver;
    private SubmissionServiceDriver submissionServiceDriver;
    private VerdictPublisher verdictPublisher;
    private CompilerFactory compilerFactory;
    private TestcaseExecutorFactory testcaseExecutorFactory;

    public CCJudger(JudgerWorkspace judgerWorkspace,
                    JudgeGirlPluginLocator pluginLocator,
                    ProblemServiceDriver problemServiceDriver,
                    SubmissionServiceDriver submissionServiceDriver,
                    VerdictPublisher verdictPublisher,
                    CompilerFactory compilerFactory,
                    TestcaseExecutorFactory testcaseExecutorFactory) {
        super(pluginLocator);
        this.judgerWorkspace = judgerWorkspace;
        this.problemServiceDriver = problemServiceDriver;
        this.submissionServiceDriver = submissionServiceDriver;
        this.verdictPublisher = verdictPublisher;
        this.compilerFactory = compilerFactory;
        this.testcaseExecutorFactory = testcaseExecutorFactory;
    }

    @Override
    protected Problem findProblemById(int problemId) {
        var problem = ProblemView.toEntity(problemServiceDriver.getProblem(problemId));
        logger.info(problem);
        return problem;
    }

    @Override
    protected List<Testcase> findTestcasesByProblemId(int problemId) {
        return problemServiceDriver.getTestcases(problemId);
    }

    @Override
    protected Submission findSubmissionByIds(int problemId, int studentId, String submissionId) {
        return SubmissionView.toEntity(submissionServiceDriver.getSubmission(studentId,
                problemId, submissionId));
    }

    @SneakyThrows
    @Override
    protected void setupJudgerFileLayout() {
        mkdirIfNotExists(judgerWorkspace.getLogHomePath());
    }

    @Override
    protected void downloadProvidedCodes() throws IOException {
        SubmissionHome submissionHome = judgerWorkspace.getSubmissionHome(getSubmission().getId());
        FileResource zip = problemServiceDriver.downloadProvidedCodes(
                getProblem().getId(), getProblem().getProvidedCodesFileId());

        ZipUtils.unzipToDestination(zip.getInputStream(),
                submissionHome.getSourceRoot().getPath());

        logger.info("Downloaded Provided Codes.");
    }

    @Override
    protected void downloadSubmittedCodes() throws IOException {
        SubmissionHome submissionHome = judgerWorkspace.getSubmissionHome(getSubmission().getId());
        FileResource zip = submissionServiceDriver.downloadSubmittedCodes(
                getProblem().getId(), getStudent(),
                getSubmission().getId(), getSubmission().getSubmittedCodesFileId()
        );

        ZipUtils.unzipToDestination(zip.getInputStream(),
                submissionHome.getSourceRoot().getPath());
        logger.info("Downloaded Submitted Codes.");
    }

    @Override
    protected void downloadTestcaseIOs() throws IOException {
        SubmissionHome submissionHome = getSubmissionHome();
        FileResource zip = problemServiceDriver.downloadTestCaseIOs(
                getProblem().getId(), getProblem().getTestcaseIOsFileId());
        ZipUtils.unzipToDestination(zip.getInputStream(), submissionHome.getPath());

        logger.info("Downloaded Testcase IOs.");
    }

    @Override
    @SneakyThrows
    protected CompileResult doCompile() {
        String script = getProblem().getCompilation().getScript();
        Files.write(getCompileScriptPath(), script.getBytes());
        Compiler compiler = compilerFactory.create(getSourceRoot().getPath());
        CompileResult result = compiler.compile(getProblem().getCompilation());
        logger.info("Compile result: " + result);
        return result;
    }

    @Override
    @SneakyThrows
    protected void onBeforeRunningTestcase(Testcase testcase) {
        copyExecutableIntoSandboxRoot(testcase);
        // TODO should also copy provided codes, this will fail in an interpreted language case
        copyInterpretedSubmittedCodesIntoSandboxRoot(testcase);
    }

    private void copyExecutableIntoSandboxRoot(Testcase testcase) throws IOException {
        Path executablePath = getSourceRoot().getExecutablePath();
        Path sandboxRootPath = getSandboxRoot(testcase).getPath();
        FileUtils.copyFile(executablePath.toFile(),
                sandboxRootPath.resolve(executablePath.getFileName()).toFile());
    }

    private void copyInterpretedSubmittedCodesIntoSandboxRoot(Testcase testcase) throws IOException {
        // the program might execute the interpreted codes from the sandbox root
        // hence we must copy them to there
        SandboxRoot sandboxRoot = getSandboxRoot(testcase);
        for (SubmittedCodeSpec submittedCodeSpec : getProblem().getSubmittedCodeSpecs()) {
            if (submittedCodeSpec.getLanguage().isInterpretedLang()) {
                Path interpretedSubmittedCodePath = getSourceRoot().getPath().resolve(submittedCodeSpec.getFileName());
                Path copyDestinationPath = sandboxRoot.getPath().resolve(submittedCodeSpec.getFileName());
                FileUtils.copyFile(interpretedSubmittedCodePath.toFile(), copyDestinationPath.toFile());
            }
        }
    }

    @Override
    protected TestcaseExecutionResult runTestcase(Testcase testcase) {
        TestcaseExecutor testcaseExecutor =
                testcaseExecutorFactory.create(getSubmission().getId(),
                        testcase, judgerWorkspace);
        TestcaseExecutionResult result = testcaseExecutor.executeProgramByProfiler(
                judgerWorkspace.getProfilerPath());
        logger.info("Completed the execution of the testcases, result: " + result);
        return result;
    }

    @Override
    protected Path getActualStandardOutputPath(Testcase testcase) {
        return getTestcaseHome(testcase)
                .getSandboxRoot().getActualStandardOutPath();
    }

    @Override
    protected Path getExpectStandardOutputPath(Testcase testcase) {
        return getTestcaseHome(testcase)
                .getTestcaseOutputHome().getExpectedStandardOutPath();
    }

    @Override
    protected Map<Path, Path> getActualToExpectOutputFilePathMap(Testcase testcase) {
        HashMap<Path, Path> mapping = new HashMap<>();
        SandboxRoot sandboxRoot = getSandboxRoot(testcase);
        TestCaseOutputHome testCaseOutputHome = getTestcaseOutputHome(testcase);
        for (String outputFileName : getProblem().getOutputFileNames()) {
            mapping.put(
                    sandboxRoot.getPath().resolve(outputFileName),
                    testCaseOutputHome.getPath().resolve(outputFileName));
        }
        return mapping;
    }

    @Override
    protected Path getSourceRootPath() {
        return getSourceRoot().getPath();
    }

    protected TestCaseOutputHome getTestcaseOutputHome(Testcase testcase) {
        return getTestcaseHome(testcase).getTestcaseOutputHome();
    }

    protected SandboxRoot getSandboxRoot(Testcase testcase) {
        return getTestcaseHome(testcase).getSandboxRoot();
    }

    protected TestcaseHome getTestcaseHome(Testcase testcase) {
        return getSubmissionHome().getTestCaseHome(testcase.getName());
    }

    protected SourceRoot getSourceRoot() {
        return getSubmissionHome().getSourceRoot();
    }

    protected Path getCompileScriptPath() {
        return getSubmissionHome().getSourceRoot().getCompileScriptPath();
    }

    protected SubmissionHome getSubmissionHome() {
        return judgerWorkspace.getSubmissionHome(getSubmission().getId());
    }

    @Override
    protected void publishVerdict(Verdict verdict) {
        verdictPublisher.publish(
                new VerdictIssuedEvent(getProblem().getId(),
                        getProblem().getTitle(),
                        getSubmission().getId(),
                        verdict.getCompileErrorMessage(),
                        verdict.getIssueTime(),
                        ReportView.fromEntity(verdict.getReport()),
                        verdict.getJudges()));
    }

    @SneakyThrows
    private void mkdirIfNotExists(Path directoryPath) {
        if (!Files.exists(directoryPath)) {
            FileUtils.forceMkdir(directoryPath.toFile());
        }
    }


}
