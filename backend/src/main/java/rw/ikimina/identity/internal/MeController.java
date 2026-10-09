package rw.ikimina.identity.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own profile (spec 17.1). {@code GET /me/groups} lives in the groups module. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    /** Only the fields a person may change about themselves; anything else in the body is rejected. */
    record UpdateProfileRequest(@Size(min = 1, max = 200) String fullName, @Pattern(regexp = "en|rw") String locale) {
    }

    private final ProfileService profiles;

    MeController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    ProfileService.Profile me() {
        return profiles.current();
    }

    @PatchMapping
    ProfileService.Profile update(@Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(request.fullName(), request.locale());
    }
}
