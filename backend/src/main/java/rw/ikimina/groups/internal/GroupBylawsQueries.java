package rw.ikimina.groups.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.groups.GroupBylaws;
import rw.ikimina.shared.tenancy.TenantContext;
import tools.jackson.databind.json.JsonMapper;

@Component
@Transactional(readOnly = true)
class GroupBylawsQueries implements GroupBylaws {

    private final GroupSettingsRepository settings;
    private final JsonMapper json;

    GroupBylawsQueries(GroupSettingsRepository settings, JsonMapper json) {
        this.settings = settings;
        this.json = json;
    }

    @Override
    public Bylaws current() {
        long groupId = TenantContext.requireGroup().groupId();
        GroupSettingsV1 stored = settings.findById(groupId)
                .map(row -> json.readValue(row.getSettings(), GroupSettingsV1.class))
                .orElseGet(GroupSettingsV1::defaults);
        return new Bylaws(InterestRecognition.valueOf(stored.interestRecognition().name()), stored.withdrawalsAllowed(),
                stored.withdrawalNoticeDays(), stored.exitFee());
    }
}
