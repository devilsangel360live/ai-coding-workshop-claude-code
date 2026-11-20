import com.example.taskmanager.infrastructure.legacy.TransformType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformTypeTest {

    @ParameterizedTest
    @CsvSource({"1, FORMAT", "2, SENTENCE", "0, PASS_THROUGH", "3, PASS_THROUGH", "-1, PASS_THROUGH", "99, PASS_THROUGH"})
    void fromCode_v1ImportCode_mapsToTransformType(int code, TransformType expected) {
        assertEquals(expected, TransformType.fromCode(code));
    }
}
