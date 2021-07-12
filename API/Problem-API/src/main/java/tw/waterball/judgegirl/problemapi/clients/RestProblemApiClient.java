package tw.waterball.judgegirl.problemapi.clients;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tw.waterball.judgegirl.commons.exceptions.NotFoundException;
import tw.waterball.judgegirl.commons.models.files.FileResource;
import tw.waterball.judgegirl.problemapi.views.ProblemView;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static tw.waterball.judgegirl.api.utils.RestTemplateUtils.*;


/**
 * @author - wally55077@gmail.com
 */
public class RestProblemApiClient implements ProblemServiceDriver {

    public static final String API_PREFIX = "/api/problems";
    private final Supplier<String> tokenSupplier;
    private final RestTemplate restTemplate;

    public RestProblemApiClient(RestTemplate restTemplate, Supplier<String> tokenSupplier) {
        this.restTemplate = restTemplate;
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public Optional<ProblemView> getProblem(int problemId) {
        try {
            String url = parsePath(API_PREFIX + "/{problemId}", problemId);
            HttpEntity<?> entity = new HttpEntity<>(withBearerTokenHeader(tokenSupplier.get()));
            return ofNullable(restTemplate.exchange(url, HttpMethod.GET, entity, ProblemView.class).getBody());
        } catch (RestClientException e) {
            return empty();
        }
    }

    @Override
    public FileResource downloadProvidedCodes(int problemId, String languageEnvName, String providedCodesFileId) throws NotFoundException {
        try {
            String url = parsePath(API_PREFIX + "/{problemId}/{langEnvName}/providedCodes/{providedCodesFileId}",
                    problemId, languageEnvName, providedCodesFileId);
            HttpEntity<?> entity = new HttpEntity<>(withBearerTokenHeader(tokenSupplier.get()));
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            return parseFileResourceFromResponse(response);
        } catch (RestClientException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @Override
    public FileResource downloadTestCaseIOs(int problemId, String testcaseId) throws NotFoundException {
        try {
            String url = parsePath(API_PREFIX + "/{problemId}/testcases/{testcaseId}/io",
                    problemId, testcaseId);
            HttpEntity<?> entity = new HttpEntity<>(withBearerTokenHeader(tokenSupplier.get()));
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            return parseFileResourceFromResponse(response);
        } catch (RestClientException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @Override
    public List<ProblemView> getProblemsByIds(List<Integer> ids) {
        try {
            String idsSplitByComma = ids.stream().map(String::valueOf).collect(joining(","));
            String url = parsePath(API_PREFIX + "?ids=", idsSplitByComma);
            HttpEntity<?> entity = new HttpEntity<>(withBearerTokenHeader(tokenSupplier.get()));
            ResponseEntity<ProblemView[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ProblemView[].class);
            ProblemView[] problemViews = response.getBody();
            return asList(requireNonNullElseGet(problemViews, () -> new ProblemView[0]));
        } catch (RestClientException e) {
            return emptyList();
        }
    }
}
