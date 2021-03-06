package tw.waterball.judgegirl.springboot.submission.controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tw.waterball.judgegirl.commons.token.TokenService;
import tw.waterball.judgegirl.springboot.submission.presenters.SubmissionsPresenter;
import tw.waterball.judgegirl.submission.domain.usecases.FindBestRecordUseCase;
import tw.waterball.judgegirl.submission.domain.usecases.GetSubmissionsUseCase;
import tw.waterball.judgegirl.submission.domain.usecases.query.SortBy;
import tw.waterball.judgegirl.submissionapi.views.SubmissionView;

import java.util.List;
import java.util.Map;

import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.ok;
import static tw.waterball.judgegirl.submission.domain.usecases.query.SubmissionQueryParams.query;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@CrossOrigin
@AllArgsConstructor
@RequestMapping("/api/submissions")
@RestController
public class SubmissionQueryController {
    private final TokenService tokenService;
    private final GetSubmissionsUseCase getSubmissionsUseCase;
    private final FindBestRecordUseCase findBestRecordUseCase;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping
    public List<SubmissionView> getSubmissions(@RequestHeader("Authorization") String authorization,
                                               @RequestParam(required = false) String[] ids,
                                               @RequestParam(required = false, defaultValue = "0") Integer page,
                                               @RequestParam(required = false) Integer problemId,
                                               @RequestParam(required = false) String langEnvName,
                                               @RequestParam(required = false) Integer studentId,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false, defaultValue = "true") Boolean ascending,
                                               @RequestParam Map<String, String> bagQueryParameters) {
        bagQueryParameters.remove("page"); // remove words preserved by other query parameters
        bagQueryParameters.remove("problemId");
        bagQueryParameters.remove("langEnvName");
        bagQueryParameters.remove("studentId");
        bagQueryParameters.remove("sortBy");
        bagQueryParameters.remove("ascending");
        return tokenService.returnIfAdmin(authorization, token -> {
            var presenter = new SubmissionsPresenter();
            if (ids != null) {
                getSubmissionsUseCase.execute(ids, presenter);
            } else {
                SortBy sort = sortBy == null ? null : new SortBy(sortBy, ascending);
                getSubmissionsUseCase.execute(query()
                        .page(page)
                        .problemId(problemId)
                        .languageEnvName(langEnvName)
                        .studentId(studentId)
                        .sortBy(sort)
                        .bagQueryParameters(bagQueryParameters).build(), presenter);
            }
            return presenter.present();
        });
    }

    @PostMapping(value = "/best", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<SubmissionView> findBestRecord(@RequestBody String submissionIdsSplitByCommas) {
        String[] submissionIds = submissionIdsSplitByCommas.split("\\s*,\\s*");
        if (submissionIds.length == 0) {
            return badRequest().build();
        }
        SubmissionPresenter presenter = new SubmissionPresenter();
        findBestRecordUseCase.execute(new FindBestRecordUseCase.Request(submissionIds), presenter);
        return ok(presenter.present());
    }
}
