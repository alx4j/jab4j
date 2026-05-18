package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.evidence.FinderRole;
import com.alx4j.jab4j.reader.capture.media.geometry.SupportedTileFinderEvaluator.Evaluation;
import com.alx4j.jab4j.reader.capture.media.geometry.SupportedTileFinderEvaluator.FinderWindow;

@DisplayName("Supported tile finder evaluator")
class SupportedTileFinderEvaluatorTest {

    private static final int DIMENSION = 13;

    private final SupportedTileFinderEvaluator evaluator = new SupportedTileFinderEvaluator();

    @Test
    @DisplayName("Exact generated candidates require all finder modules")
    void exactGeneratedCandidatesRequireAllFinderModules() {
        List<Integer> exactModules = tileModules();
        paintSupportedFinders(exactModules);
        List<Integer> partialModules = new ArrayList<>(exactModules);
        partialModules.set(0, 1);

        Evaluation exact = evaluator.evaluate(exactModules, DIMENSION);
        Evaluation partial = evaluator.evaluate(partialModules, DIMENSION);

        assertAll(
                () -> assertEquals(0, window(FinderRole.TOP_LEFT).expectedColor()),
                () -> assertEquals(0, window(FinderRole.TOP_RIGHT).expectedColor()),
                () -> assertEquals(6, window(FinderRole.BOTTOM_LEFT).expectedColor()),
                () -> assertEquals(3, window(FinderRole.BOTTOM_RIGHT).expectedColor()),
                () -> assertTrue(exact.exact()),
                () -> assertTrue(exact.supported(false)),
                () -> assertEquals(9, exact.role(FinderRole.TOP_LEFT).matchedModuleCount()),
                () -> assertFalse(partial.exact()),
                () -> assertFalse(partial.supported(false)),
                () -> assertEquals(8, partial.role(FinderRole.TOP_LEFT).matchedModuleCount())
        );
    }

    @Test
    @DisplayName("Camera-derived candidates are recoverable at configured thresholds")
    void cameraDerivedCandidatesAreRecoverableAtConfiguredThresholds() {
        List<Integer> modules = tileModules();
        paintRoleMatches(modules, FinderRole.TOP_LEFT, 9);
        paintRoleMatches(modules, FinderRole.TOP_RIGHT, 6);
        paintRoleMatches(modules, FinderRole.BOTTOM_LEFT, 6);

        Evaluation evaluation = evaluator.evaluate(modules, DIMENSION);

        assertAll(
                () -> assertFalse(evaluation.exact()),
                () -> assertFalse(evaluation.supported(false)),
                () -> assertTrue(evaluation.supported(true)),
                () -> assertEquals(3, evaluation.recoverableCount()),
                () -> assertEquals(6, evaluation.role(FinderRole.TOP_RIGHT).matchedModuleCount()),
                () -> assertTrue(evaluation.recoverableWithAverageConfidence(0.30d)),
                () -> assertFalse(evaluation.recoverableWithAverageConfidence(0.299d))
        );
    }

    @Test
    @DisplayName("Wrong role colors do not satisfy finder matches")
    void wrongRoleColorsDoNotSatisfyFinderMatches() {
        List<Integer> modules = tileModules();
        paintRoleMatches(modules, FinderRole.TOP_LEFT, 9);
        paintRoleMatches(modules, FinderRole.TOP_RIGHT, 9);
        paintRoleColor(modules, FinderRole.BOTTOM_LEFT, 3);
        paintRoleColor(modules, FinderRole.BOTTOM_RIGHT, 6);

        Evaluation evaluation = evaluator.evaluate(modules, DIMENSION);

        assertAll(
                () -> assertEquals(0, evaluation.role(FinderRole.BOTTOM_LEFT).matchedModuleCount()),
                () -> assertEquals(9, evaluation.role(FinderRole.BOTTOM_LEFT).expectedModuleCount()),
                () -> assertEquals(0, evaluation.role(FinderRole.BOTTOM_RIGHT).matchedModuleCount()),
                () -> assertEquals(2, evaluation.recoverableCount()),
                () -> assertFalse(evaluation.supported(true))
        );
    }

    @Test
    @DisplayName("Insufficient partial finders are not recoverable")
    void insufficientPartialFindersAreNotRecoverable() {
        List<Integer> modules = tileModules();
        paintRoleMatches(modules, FinderRole.TOP_LEFT, 6);
        paintRoleMatches(modules, FinderRole.TOP_RIGHT, 6);
        paintRoleMatches(modules, FinderRole.BOTTOM_LEFT, 5);
        paintRoleMatches(modules, FinderRole.BOTTOM_RIGHT, 5);

        Evaluation evaluation = evaluator.evaluate(modules, DIMENSION);

        assertAll(
                () -> assertEquals(2, evaluation.recoverableCount()),
                () -> assertFalse(evaluation.supported(true)),
                () -> assertFalse(evaluation.recoverableWithAverageConfidence(1.0d))
        );
    }

    @Test
    @DisplayName("Aggregate confidence uses all expected finder modules")
    void aggregateConfidenceUsesAllExpectedFinderModules() {
        List<Integer> modules = tileModules();
        paintRoleMatches(modules, FinderRole.TOP_LEFT, 9);
        paintRoleMatches(modules, FinderRole.TOP_RIGHT, 8);
        paintRoleMatches(modules, FinderRole.BOTTOM_LEFT, 7);
        paintRoleMatches(modules, FinderRole.BOTTOM_RIGHT, 6);

        Evaluation evaluation = evaluator.evaluate(modules, DIMENSION);

        assertAll(
                () -> assertEquals(30, evaluation.matchedModuleCount()),
                () -> assertEquals(36, evaluation.expectedModuleCount()),
                () -> assertEquals(30.0d / 36.0d, evaluation.aggregateConfidence(), 0.000001d),
                () -> assertEquals(8.0d / 9.0d, evaluation.role(FinderRole.TOP_RIGHT).confidence(), 0.000001d)
        );
    }

    private List<Integer> tileModules() {
        return new ArrayList<>(Collections.nCopies(DIMENSION * DIMENSION, 1));
    }

    private void paintSupportedFinders(List<Integer> modules) {
        for (FinderWindow finderWindow : evaluator.finderWindows(DIMENSION)) {
            paintRoleMatches(modules, finderWindow.role(), finderWindow.sizeModules() * finderWindow.sizeModules());
        }
    }

    private void paintRoleMatches(List<Integer> modules, FinderRole role, int matchedModules) {
        FinderWindow finderWindow = window(role);
        int painted = 0;
        for (int row = finderWindow.startRow();
                row < finderWindow.startRow() + finderWindow.sizeModules();
                row++) {
            for (int col = finderWindow.startCol();
                    col < finderWindow.startCol() + finderWindow.sizeModules();
                    col++) {
                int color = painted < matchedModules
                        ? finderWindow.expectedColor()
                        : wrongColor(finderWindow.expectedColor());
                modules.set((row * DIMENSION) + col, color);
                painted++;
            }
        }
    }

    private void paintRoleColor(List<Integer> modules, FinderRole role, int color) {
        FinderWindow finderWindow = window(role);
        for (int row = finderWindow.startRow();
                row < finderWindow.startRow() + finderWindow.sizeModules();
                row++) {
            for (int col = finderWindow.startCol();
                    col < finderWindow.startCol() + finderWindow.sizeModules();
                    col++) {
                modules.set((row * DIMENSION) + col, color);
            }
        }
    }

    private int wrongColor(int expectedColor) {
        return (expectedColor + 1) % 8;
    }

    private FinderWindow window(FinderRole role) {
        return evaluator.finderWindows(DIMENSION).stream()
                .filter(finderWindow -> finderWindow.role() == role)
                .findFirst()
                .orElseThrow();
    }
}
