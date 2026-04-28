package pro.alx4j.jab4j.render.layout;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.api.model.LayoutProfile;

@DisplayName("Fixed layout planning")
class FixedLayoutPlannerTest {

    private final FixedLayoutPlanner planner = new FixedLayoutPlanner();

    @Test
    @DisplayName("The safe desktop profile produces the expected geometry")
    void safeDesktopProfileProducesExpectedGeometry() {
        LayoutProfile profile = safeDesktopProfile();

        FixedLayoutPlan plan = planner.plan(profile);

        assertAll(
                () -> assertEquals(48, plan.gridOriginXPx()),
                () -> assertEquals(144, plan.gridOriginYPx()),
                () -> assertEquals(1800, plan.usableGridWidthPx()),
                () -> assertEquals(864, plan.usableGridHeightPx()),
                () -> assertEquals(900, plan.tileSlotWidthPx()),
                () -> assertEquals(432, plan.tileSlotHeightPx()),
                () -> assertEquals(12, plan.separatorThicknessPx()),
                () -> assertEquals(4, plan.tilePlacements().size()),
                () -> assertEquals(972, plan.tilePlacements().get(1).xPx()),
                () -> assertEquals(600, plan.tilePlacements().get(2).yPx())
        );
    }

    @Test
    @DisplayName("Tiny tile slots fail layout validation")
    void tinyTileSlotsFailLayoutValidation() {
        LayoutProfile profile = new LayoutProfile(
                "unsafe",
                4,
                4,
                640,
                480,
                24,
                48,
                "solidWhite",
                64,
                32,
                "black",
                "preserveAspect"
        );

        LayoutValidationException exception = assertThrows(LayoutValidationException.class, () -> planner.plan(profile));

        assertEquals("layout tile slots must remain at least 64x64 pixels", exception.getMessage());
    }

    @Test
    @DisplayName("Unsupported visual styles fail clearly")
    void unsupportedVisualStylesFailClearly() {
        LayoutProfile profile = new LayoutProfile(
                "unsupported-style",
                2,
                2,
                1920,
                1080,
                24,
                48,
                "dashed",
                64,
                32,
                "black",
                "preserveAspect"
        );

        LayoutValidationException exception = assertThrows(LayoutValidationException.class, () -> planner.plan(profile));

        assertEquals("Unsupported separatorStyle: dashed", exception.getMessage());
    }

    private LayoutProfile safeDesktopProfile() {
        return new LayoutProfile(
                "desktop-1080p-safe",
                2,
                2,
                1920,
                1080,
                24,
                48,
                "solidWhite",
                64,
                32,
                "black",
                "preserveAspect"
        );
    }
}
