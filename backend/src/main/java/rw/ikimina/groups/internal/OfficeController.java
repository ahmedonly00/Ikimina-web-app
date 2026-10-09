package rw.ikimina.groups.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.security.RequiresRecentAuthentication;

/**
 * Transfer of office (spec 17.2). Who may act is decided by who holds the office or is
 * receiving it, not by a matrix permission - so these are open to any member and the
 * service enforces the parties.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/offices")
class OfficeController {

    record OfferRequest(@NotNull GroupRole role, @NotNull UUID toMemberId) {
    }

    private final OfficeTransferService transfers;

    OfficeController(OfficeTransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @GroupAccess.AnyMember
    @RequiresRecentAuthentication
    OfficeTransferService.TransferView offer(@PathVariable UUID groupId, @Valid @RequestBody OfferRequest request) {
        return transfers.offer(request.role(), request.toMemberId());
    }

    @GetMapping("/transfers")
    @GroupAccess.AnyMember
    List<OfficeTransferService.TransferView> pending(@PathVariable UUID groupId) {
        return transfers.pending();
    }

    @PostMapping("/transfers/{transferId}/accept")
    @GroupAccess.AnyMember
    OfficeTransferService.TransferView accept(@PathVariable UUID groupId, @PathVariable UUID transferId) {
        return transfers.accept(transferId);
    }

    @PostMapping("/transfers/{transferId}/decline")
    @GroupAccess.AnyMember
    OfficeTransferService.TransferView decline(@PathVariable UUID groupId, @PathVariable UUID transferId) {
        return transfers.decline(transferId);
    }

    @PostMapping("/transfers/{transferId}/cancel")
    @GroupAccess.AnyMember
    OfficeTransferService.TransferView cancel(@PathVariable UUID groupId, @PathVariable UUID transferId) {
        return transfers.cancel(transferId);
    }
}
