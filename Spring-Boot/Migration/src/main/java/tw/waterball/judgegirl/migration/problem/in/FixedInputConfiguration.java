package tw.waterball.judgegirl.migration.problem.in;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import tw.waterball.judgegirl.entities.problem.ResourceSpec;
import tw.waterball.judgegirl.plugins.impl.match.AllMatchPolicyPlugin;

import java.nio.file.Paths;
import java.util.OptionalInt;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Profile("fixed-input")
@Configuration
public class FixedInputConfiguration {

    @Bean
    @Primary
    public MigrateOneProblemStandardInput migrateOneProblemStandardInput() {
        int problemId = 3;
        return MigrateOneProblemStandardInput.builder()
                .problemId(problemId)
                .compilationScript("gcc -std=c99 -O2 a.c -lm")
                .tags(new String[]{"arithmetic operation, basic I/O"})
                .legacyPackageRootPath(Paths.get("/Volumes/Legacy-Judge-Girl-2021/package"))
                .outputDirectoryPath(Paths.get("temp/migration/" + problemId))
                .resourceSpec(new ResourceSpec(1, 0))
                .matchPolicyPlugin(new AllMatchPolicyPlugin())
                .replaceExistingProblemOrNot(problems -> problems.stream()
                        .filter(p -> p.getId() == problemId).findFirst())
                .specifyProblemIdOrNot((problems) -> OptionalInt.of(problemId))
                .build();
    }
}
