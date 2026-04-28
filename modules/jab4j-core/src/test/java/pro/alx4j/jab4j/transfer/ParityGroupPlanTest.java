package pro.alx4j.jab4j.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.api.model.ParityGroupSizingStrategy;

@DisplayName("Parity group planning")
class ParityGroupPlanTest {

    @Test
    @DisplayName("Source chunk counts must match the declared range")
    void rejectsSourceChunkCountThatDoesNotMatchTheDeclaredRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new ParityGroupPlan(
                0,
                3,
                1,
                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                0,
                3,
                2
        ));

        assertEquals("source chunk range must match sourceChunkCount", exception.getMessage());
    }
}
