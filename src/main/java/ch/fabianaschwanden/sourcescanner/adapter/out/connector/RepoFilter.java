package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import java.util.Optional;
import java.util.regex.Pattern;

/** Optionaler Repo-Namens-Regex-Filter für die Discovery (IR-06). */
final class RepoFilter {

    private RepoFilter() {
    }

    static Optional<Pattern> compile(String repoFilter) {
        if (repoFilter == null || repoFilter.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Pattern.compile(repoFilter));
    }

    static boolean matches(Optional<Pattern> filter, String name) {
        return filter.isEmpty() || filter.get().matcher(name).find();
    }
}
