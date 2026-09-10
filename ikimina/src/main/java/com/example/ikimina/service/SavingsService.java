package com.example.ikimina.service;
import java.math.BigDecimal;
import com.example.ikimina.money.Money;
import com.example.ikimina.ledger.LedgerCommand;
import com.example.ikimina.ledger.LedgerDirection;
import com.example.ikimina.ledger.LedgerEntry;
import com.example.ikimina.ledger.LedgerEntryRepository;
import com.example.ikimina.ledger.LedgerEntryType;
import com.example.ikimina.ledger.LedgerService;
import com.example.ikimina.security.SecurityUtils;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.SavingsDTO;
import com.example.ikimina.dto.BulkSavingsEntryDTO;
import com.example.ikimina.dto.MemberSavingsLedgerDTO;
import com.example.ikimina.model.Savings;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.SavingsRepository;
import com.example.ikimina.repository.UserRepository;
import com.example.ikimina.enums.SavingsType;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SavingsService {

    private static final String SOURCE_SAVINGS = "SAVINGS";
    
    @Autowired
    private SavingsRepository savingsRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    /**
     * Records a contribution and its ledger entry in one transaction.
     *
     * The idempotency key makes a retried request safe: a member's contribution
     * cannot be double-counted because the request timed out on a slow
     * connection. Callers should pass a stable key (the Idempotency-Key header);
     * when absent one is derived from the contribution's natural identity.
     */
    @Transactional
    public SavingsDTO createSavings(SavingsDTO savingsDTO, String idempotencyKey) {
        User user = userRepository.findById(savingsDTO.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate date = savingsDTO.getDate() != null ? savingsDTO.getDate() : LocalDate.now();
        BigDecimal amount = Money.of(savingsDTO.getAmount());

        if (!Money.isPositive(amount)) {
            throw new BusinessRuleException("Savings amount must be greater than zero");
        }

        Long groupId = userRepository.findPrimaryGroupId(user.getId());
        if (groupId == null) {
            throw new BusinessRuleException("Member does not belong to a savings group");
        }

        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? naturalKey(user.getId(), date, savingsDTO.getType(), amount)
                : idempotencyKey;

        // If this exact contribution was already recorded, return it rather than
        // creating a duplicate.
        List<LedgerEntry> already = ledgerEntryRepository.findByIdempotencyKey(key)
                .map(List::of)
                .orElseGet(List::of);
        if (!already.isEmpty()) {
            LedgerEntry entry = already.get(0);
            if (entry.getSourceId() != null) {
                return savingsRepository.findById(entry.getSourceId())
                        .map(this::convertToDTO)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Ledger entry references a missing savings row: " + entry.getSourceId()));
            }
        }

        Savings savings = new Savings();
        savings.setAmount(amount);
        savings.setType(savingsDTO.getType());
        savings.setDate(date);
        savings.setUser(user);

        Savings savedSavings = savingsRepository.save(savings);

        ledgerService.record(new LedgerCommand(
                LedgerEntryType.SAVINGS_CONTRIBUTION,
                LedgerDirection.CREDIT,
                amount,
                LedgerService.DEFAULT_CURRENCY,
                groupId,
                user.getId(),
                date,
                key,
                SOURCE_SAVINGS,
                savedSavings.getId(),
                SecurityUtils.currentPrincipal().map(p -> p.getUserId()).orElse(user.getId()),
                savingsDTO.getType() + " contribution"
        ));

        return convertToDTO(savedSavings);
    }

    /** Backwards-compatible overload; derives the idempotency key. */
    @Transactional
    public SavingsDTO createSavings(SavingsDTO savingsDTO) {
        return createSavings(savingsDTO, null);
    }

    /**
     * Natural identity of a contribution. Two identical submissions for the same
     * member, date, type and amount are treated as the same contribution, which
     * is the safe default when no explicit key is supplied.
     */
    private String naturalKey(Long userId, LocalDate date, SavingsType type, BigDecimal amount) {
        return "savings:" + userId + ":" + date + ":" + type + ":" + amount.toPlainString();
    }
    
    /** Paged history. Member histories grow without bound over a group's life. */
    public Page<SavingsDTO> getUserSavingsPaged(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return savingsRepository.findByUser(user, pageable).map(this::convertToDTO);
    }

    public List<SavingsDTO> getUserSavings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return savingsRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public BigDecimal getTotalSavingsForDate(LocalDate date) {
        return savingsRepository.getTotalSavingsForDate(date);
    }
    
    public BigDecimal getUserTotalSavingsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return savingsRepository.getTotalSavingsForUserBetweenDates(user, startDate, endDate);
    }
    
    public List<SavingsDTO> getUserSavingsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return savingsRepository.findByUserAndDateBetween(user, startDate, endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private SavingsDTO convertToDTO(Savings savings) {
        return new SavingsDTO(
                savings.getId(),
                savings.getAmount(),
                savings.getType(),
                savings.getDate(),
                savings.getUser().getId()
        );
    }
    
    /**
     * Records a meeting's worth of contributions.
     *
     * Each row goes through createSavings so it gets its own ledger entry and
     * idempotency key. A per-member, per-type key derived from the batch key
     * means re-submitting the whole sheet after a dropped connection re-records
     * nothing - the common failure mode when an admin enters a meeting on a
     * patchy connection.
     */
    @Transactional
    public List<SavingsDTO> createBulkSavings(BulkSavingsEntryDTO bulkEntry, String batchKey) {
        LocalDate date = bulkEntry.getDate() != null ? bulkEntry.getDate() : LocalDate.now();
        List<SavingsDTO> created = new ArrayList<>();

        for (BulkSavingsEntryDTO.MemberSavingsDTO memberSavings : bulkEntry.getMembers()) {
            if (Money.isPositive(memberSavings.getUbwizigameAmount())) {
                created.add(createSavings(
                        entryFor(memberSavings.getUserId(), memberSavings.getUbwizigameAmount(),
                                SavingsType.UBWIZIGAME, date),
                        bulkKey(batchKey, memberSavings.getUserId(), SavingsType.UBWIZIGAME, date)));
            }

            if (Money.isPositive(memberSavings.getIngobokaAmount())) {
                created.add(createSavings(
                        entryFor(memberSavings.getUserId(), memberSavings.getIngobokaAmount(),
                                SavingsType.INGOBOKA, date),
                        bulkKey(batchKey, memberSavings.getUserId(), SavingsType.INGOBOKA, date)));
            }
        }

        return created;
    }

    /** Backwards-compatible overload. */
    @Transactional
    public List<SavingsDTO> createBulkSavings(BulkSavingsEntryDTO bulkEntry) {
        return createBulkSavings(bulkEntry, null);
    }

    private SavingsDTO entryFor(Long userId, BigDecimal amount, SavingsType type, LocalDate date) {
        SavingsDTO dto = new SavingsDTO();
        dto.setUserId(userId);
        dto.setAmount(amount);
        dto.setType(type);
        dto.setDate(date);
        return dto;
    }

    private String bulkKey(String batchKey, Long userId, SavingsType type, LocalDate date) {
        if (batchKey == null || batchKey.isBlank()) {
            return null; // falls back to the natural key in createSavings
        }
        return "bulk:" + batchKey + ":" + userId + ":" + type + ":" + date;
    }
    
    public List<MemberSavingsLedgerDTO> getMemberSavingsLedger(List<Long> userIds, LocalDate startDate, LocalDate endDate) {
        List<Savings> savings = savingsRepository.findByUserIdsAndDateBetween(userIds, startDate, endDate);
        
        Map<Long, MemberSavingsLedgerDTO> ledgerMap = new HashMap<>();
        
        for (Savings saving : savings) {
            Long userId = saving.getUser().getId();
            String userName = saving.getUser().getFirstName() + " " + saving.getUser().getLastName();
            
            MemberSavingsLedgerDTO ledger = ledgerMap.computeIfAbsent(userId, id -> {
                MemberSavingsLedgerDTO dto = new MemberSavingsLedgerDTO();
                dto.setMemberId(id);
                dto.setMemberName(userName);
                dto.setDailySavings(new HashMap<>());
                return dto;
            });
            
            MemberSavingsLedgerDTO.DailySavingsDTO dailySavings = 
                ledger.getDailySavings().computeIfAbsent(saving.getDate(), date -> {
                    MemberSavingsLedgerDTO.DailySavingsDTO dto = new MemberSavingsLedgerDTO.DailySavingsDTO();
                    dto.setUbwizigame(Money.ZERO);
                    dto.setIngoboka(Money.ZERO);
                    return dto;
                });
            
            // Accumulate: a member may contribute more than once on the same
            // date, and assigning here would discard every entry but the last.
            if (saving.getType() == SavingsType.UBWIZIGAME) {
                dailySavings.setUbwizigame(Money.add(dailySavings.getUbwizigame(), saving.getAmount()));
            } else if (saving.getType() == SavingsType.INGOBOKA) {
                dailySavings.setIngoboka(Money.add(dailySavings.getIngoboka(), saving.getAmount()));
            }
        }
        
        return new ArrayList<>(ledgerMap.values());
    }
}
