package annoyance;

import static java.lang.System.getenv;
import static java.util.Arrays.asList;

import annoyance.model.Destination;
import annoyance.model.PullRequest;
import annoyance.model.Repository;
import annoyance.model.Schedule;
import annoyance.model.Source;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import org.kohsuke.github.GitHub;

public class Nag {

    private final Map<String, String> env;
    private final Schedule schedule;

    public Nag(final Schedule schedule, final Map<String, String> env) {
        this.schedule = schedule;
        this.env = env;
    }

    public Stream<PullRequest> tasks() {
        return this.schedule.find(this.env).entrySet().stream()
                .map((task) -> new SimpleEntry<>(task.getKey(), task.getValue().split(":")))
                .map(this::toPullRequest);
    }

    public PullRequest toPullRequest(final Entry<String, String[]> task) {
        // We need mutable lists
        final String[] job = task.getValue();
        final List<String> src = new ArrayList<>(asList(job[0].split("/")));
        final List<String> dst = new ArrayList<>(asList(job[1].split("/")));
        final Optional<String> message = Optional.ofNullable((job.length < 3) || job[2].isEmpty() ? null : job[2]);
        final Source source = new Source(new Repository(src), src.iterator().next());
        final Destination destination = new Destination(new Repository(dst), String.join("/", dst), message);
        return new PullRequest(task.getKey(), source, destination);
    }

    public static void main(final String[] args) throws IOException {
        final Map<String, String> env = getenv();
        final GitHub github = GitHub.connect("x-oauth-basic", env.get("GITHUB_TOKEN"));
        run(github, LocalDate.now(Clock.systemUTC()), env);
    }

    public static void run(final GitHub github, final LocalDate now, final Map<String, String> env) {
        switch(now.getDayOfWeek()) {
            case FRIDAY:
                run(Schedule.weekly, github, env);
            default:
                run(Schedule.daily, github, env);

        }
    }

    public static void run(final Schedule schedule, final GitHub github, final Map<String, String> env) {
        new Nag(schedule, env).tasks()
        .map((task) -> Boolean.toString(task.execute(github)) + ':' + task.toString())
        .forEach(System.err::println);
    }
}