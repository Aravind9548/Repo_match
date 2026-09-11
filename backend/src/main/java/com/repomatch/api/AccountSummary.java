package com.repomatch.api;

import java.time.Instant;

import com.repomatch.repo.GithubAccount;

public record AccountSummary(String login, String name, String avatarUrl, String htmlUrl, Instant syncedAt) {

    public static AccountSummary of(GithubAccount a) {
        return new AccountSummary(a.getDisplayLogin(), a.getName(), a.getAvatarUrl(), a.getHtmlUrl(), a.getSyncedAt());
    }
}
