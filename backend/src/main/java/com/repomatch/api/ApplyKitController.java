package com.repomatch.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.jobs.OwnerKeys;
import com.repomatch.tailor.ApplyKitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** The apply kit: standing answers kept once, and this job's answers drafted from the resume and repos. */
@RestController
@RequestMapping("/api/apply")
public class ApplyKitController {

    private final ApplyKitService kits;
    private final OwnerKeys keys;

    public ApplyKitController(ApplyKitService kits, OwnerKeys keys) {
        this.kits = kits;
        this.keys = keys;
    }

    @GetMapping("/profile")
    public ApplyKitService.Profile profile(@RequestHeader(JobsController.KEY_HEADER) String key) {
        return kits.profile(keys.resolve(key));
    }

    @PutMapping("/profile")
    public ApplyKitService.Profile saveProfile(@RequestHeader(JobsController.KEY_HEADER) String key,
                                               @Valid @RequestBody ApplyKitService.Profile body) {
        return kits.saveProfile(keys.resolve(key), body);
    }

    @GetMapping("/kits/{applicationId}")
    public ApplyKitService.Kit kit(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable long applicationId) {
        return kits.kit(keys.resolve(key), applicationId);
    }

    public record BuildBody(@Size(max = 40) String githubUsername, @Size(max = 10) String model) {
    }

    /** Paid: one model call drafting this job's answers. */
    @PostMapping("/kits/{applicationId}")
    public ApplyKitService.Kit build(@RequestHeader(JobsController.KEY_HEADER) String key,
                                     @PathVariable long applicationId, @RequestBody(required = false) BuildBody body,
                                     HttpServletRequest http) {
        return kits.build(keys.resolve(key), applicationId, body == null ? null : body.githubUsername(),
                body == null ? null : body.model(), http.getRemoteAddr());
    }
}
