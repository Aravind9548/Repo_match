package com.repomatch.jobs;

import java.time.Instant;

/**
 * One posting as read from any source, before it's stored.
 *
 * @param country  two-letter code when the source says so outright; otherwise worked out from the location
 * @param postedAt when the employer published it, if the source knows
 * @param intern   the source itself labels it an internship (e.g. Ashby's employmentType "Intern")
 */
public record FetchedPosting(String company, String role, String location, String country, boolean remote,
                             String applyUrl, boolean closed, String ageLabel, Instant postedAt, boolean intern) {
}
