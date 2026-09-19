package com.nutriverse.backend;

import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import com.nutriverse.backend.service.DailyTargetService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TargetCalculationTests {
    @Test
    void incompleteMinorOrInvalidProfilesCannotKeepStaleTargets() {
        var repository = mock(NutritionProfileRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new DailyTargetService(repository);
        var profile = adultProfile();
        when(repository.findByUserId("owner")).thenReturn(Optional.of(profile));
        assertNotNull(service.calculateTargets("owner").getDailyCalorieTarget());
        profile.setAge(12);
        assertNull(service.calculateTargets("owner").getDailyCalorieTarget());
        assertNull(profile.getDailyProteinTarget());
        assertNull(profile.getDailyWaterTarget());
        profile.setAge(25);
        profile.setWeight(Double.NaN);
        assertNull(service.calculateTargets("owner").getDailyCalorieTarget());
        profile.setWeight(70.0);
        profile.setActivityLevel(null);
        assertNull(service.calculateTargets("owner").getDailyCalorieTarget());
    }

    private NutritionProfile adultProfile() {
        var profile = new NutritionProfile("owner");
        profile.setAge(25);
        profile.setHeight(175.0);
        profile.setWeight(70.0);
        profile.setGender("MALE");
        profile.setActivityLevel("MODERATELY_ACTIVE");
        return profile;
    }
}
