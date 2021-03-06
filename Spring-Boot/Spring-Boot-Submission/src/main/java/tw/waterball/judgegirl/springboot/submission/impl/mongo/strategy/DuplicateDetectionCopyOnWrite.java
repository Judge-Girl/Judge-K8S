package tw.waterball.judgegirl.springboot.submission.impl.mongo.strategy;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;
import tw.waterball.judgegirl.commons.models.files.FileResource;
import tw.waterball.judgegirl.commons.models.files.StreamingResource;
import tw.waterball.judgegirl.primitives.submission.Submission;
import tw.waterball.judgegirl.springboot.submission.impl.mongo.data.SubmissionData;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static java.util.stream.IntStream.range;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static tw.waterball.judgegirl.commons.utils.StreamUtils.mapToList;
import static tw.waterball.judgegirl.commons.utils.ZipUtils.zipToStream;
import static tw.waterball.judgegirl.springboot.submission.impl.mongo.data.DataMapper.toData;
import static tw.waterball.judgegirl.springboot.submission.impl.mongo.data.DataMapper.toEntity;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@ConditionalOnProperty(name = "judge-girl.submission-service.save-strategy",
        havingValue = DuplicateDetectionCopyOnWrite.STRATEGY_NAME)
@AllArgsConstructor
@Component
public class DuplicateDetectionCopyOnWrite implements SaveSubmissionWithCodesStrategy {
    public static final String STRATEGY_NAME = "copy-on-write";
    protected final MongoTemplate mongoTemplate;
    protected final GridFsTemplate gridFsTemplate;

    @Override
    public Submission perform(Submission submission, List<FileResource> originalCodes, String fileName) {
        final SubmissionData data = toData(submission);
        var zip = new StreamingResource(fileName, zipAndComputeHash(data, originalCodes));
        detectDuplicateSubmissionData(data)
                .ifPresentOrElse(duplicate -> copyOnWrite(data, duplicate),
                        () -> data.setSubmittedCodesFileId(saveAndGetFileId(zip)));
        return toEntity(mongoTemplate.save(data));
    }

    @SneakyThrows
    public InputStream zipAndComputeHash(SubmissionData data, List<FileResource> codes) {
        var digestInputStreams = mapToList(codes,
                code -> new DigestInputStream(code.getInputStream(), MessageDigest.getInstance("MD5")));
        range(0, codes.size()).forEach(i -> codes.get(i).setInputStream(digestInputStreams.get(i)));
        InputStream zip = zipToStream(codes);
        data.setSubmittedCodesHash(hash(digestInputStreams));
        return zip;
    }

    private String hash(List<DigestInputStream> digestInputStreams) {
        return digestInputStreams.stream().map(this::digestToHex)
                .reduce((prev, current) -> prev + "|" + current).orElse("");
    }

    private Optional<SubmissionData> detectDuplicateSubmissionData(SubmissionData data) {
        return ofNullable(mongoTemplate.findOne(Query.query(
                where("submittedCodesHash").is(data.getSubmittedCodesHash())
                        .and("problemId").is(data.getProblemId())
                        .and("verdict").exists(true)), SubmissionData.class));
    }

    private void copyOnWrite(SubmissionData data, SubmissionData duplicate) {
        // copy on write --> directly associate to the same file's id in GridFs
        data.setSubmittedCodesFileId(duplicate.getSubmittedCodesFileId());
        onDuplicate(data, duplicate);
    }

    private String digestToHex(DigestInputStream digestInputStream) {
        Locale locale = new Locale("en","US");
        return printHexBinary(digestInputStream.getMessageDigest().digest()).toUpperCase(locale);
    }

    private String saveAndGetFileId(StreamingResource streamingResource) {
        return gridFsTemplate.store(streamingResource.getInputStream(),
                streamingResource.getFileName()).toString();
    }

    protected void onDuplicate(SubmissionData newData, SubmissionData duplicateData) {
        // hook
    }
}
