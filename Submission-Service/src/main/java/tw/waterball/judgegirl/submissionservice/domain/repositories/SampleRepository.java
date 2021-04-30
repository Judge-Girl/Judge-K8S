package tw.waterball.judgegirl.submissionservice.domain.repositories;

import java.util.List;

/**
 * @author - wally55077@gmail.com
 */
public interface SampleRepository {

    String COLLECTION_NAME = "sample";

    void upgradeSubmissionToSample(int problemId, String... submissionIds);

    List<String> findSampleSubmissionIds(int problemId);

    void downgradeSampleBackToSubmission(int problemId, String... submissionIds);

}
