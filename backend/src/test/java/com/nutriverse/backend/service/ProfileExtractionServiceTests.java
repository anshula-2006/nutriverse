package com.nutriverse.backend.service;

import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileExtractionServiceTests {

    private final NutritionProfileRepository profiles = mock(NutritionProfileRepository.class);
    private final DailyTargetService targets = mock(DailyTargetService.class);
    private final ProfileExtractionService service = new ProfileExtractionService(profiles, targets);
    private final NutritionProfile profile = new NutritionProfile("owner");

    @Test
    void temporaryUnavailableFoodIsNotSavedAsPermanentDislike() {
        when(profiles.findByUserId("owner")).thenReturn(Optional.of(profile));

        service.processUserMessage("owner", "I don't have paneer today");

        assertNull(profile.getFoodDislikes());
        verify(profiles, never()).save(any());
        verifyNoInteractions(targets);
    }

    @Test
    void explicitDislikeIsStillSaved() {
        when(profiles.findByUserId("owner")).thenReturn(Optional.of(profile));
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));

        service.processUserMessage("owner", "I don't like paneer");

        assertEquals("paneer", profile.getFoodDislikes());
        verify(profiles).save(profile);
        verify(targets).calculateTargets("owner");
    }
}
