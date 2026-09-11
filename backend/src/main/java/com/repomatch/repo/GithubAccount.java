package com.repomatch.repo;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "github_account")
public class GithubAccount {

    /** Lower-cased login, so lookups are case-insensitive like GitHub itself. */
    @Id
    private String login;
    private String displayLogin;
    private String name;
    private String avatarUrl;
    private String htmlUrl;
    private Integer publicRepos;
    private Instant syncedAt;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getDisplayLogin() { return displayLogin; }
    public void setDisplayLogin(String displayLogin) { this.displayLogin = displayLogin; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public Integer getPublicRepos() { return publicRepos; }
    public void setPublicRepos(Integer publicRepos) { this.publicRepos = publicRepos; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
