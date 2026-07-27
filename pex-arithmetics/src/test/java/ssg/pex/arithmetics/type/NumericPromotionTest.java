package ssg.pex.arithmetics.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.type.PexType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NumericPromotion")
class NumericPromotionTest {

    @Nested
    @DisplayName("promote()")
    class Promote {
        @Test void intInt() {
            assertThat(NumericPromotion.promote(PexType.INT, PexType.INT)).isEqualTo(PexType.INT);
        }

        @Test void intLong() {
            assertThat(NumericPromotion.promote(PexType.INT, PexType.LONG)).isEqualTo(PexType.LONG);
        }

        @Test void longInt() {
            assertThat(NumericPromotion.promote(PexType.LONG, PexType.INT)).isEqualTo(PexType.LONG);
        }

        @Test void intFloat() {
            assertThat(NumericPromotion.promote(PexType.INT, PexType.FLOAT)).isEqualTo(PexType.FLOAT);
        }

        @Test void intDouble() {
            assertThat(NumericPromotion.promote(PexType.INT, PexType.DOUBLE)).isEqualTo(PexType.DOUBLE);
        }

        @Test void longFloat_promotesToDouble() {
            // Special case: long + float -> double to avoid precision loss
            assertThat(NumericPromotion.promote(PexType.LONG, PexType.FLOAT)).isEqualTo(PexType.DOUBLE);
        }

        @Test void floatLong_promotesToDouble() {
            assertThat(NumericPromotion.promote(PexType.FLOAT, PexType.LONG)).isEqualTo(PexType.DOUBLE);
        }

        @Test void longDouble() {
            assertThat(NumericPromotion.promote(PexType.LONG, PexType.DOUBLE)).isEqualTo(PexType.DOUBLE);
        }

        @Test void floatDouble() {
            assertThat(NumericPromotion.promote(PexType.FLOAT, PexType.DOUBLE)).isEqualTo(PexType.DOUBLE);
        }

        @Test void floatFloat() {
            assertThat(NumericPromotion.promote(PexType.FLOAT, PexType.FLOAT)).isEqualTo(PexType.FLOAT);
        }

        @Test void doubleDouble() {
            assertThat(NumericPromotion.promote(PexType.DOUBLE, PexType.DOUBLE)).isEqualTo(PexType.DOUBLE);
        }

        @Test void nonNumericThrows() {
            assertThatThrownBy(() -> NumericPromotion.promote(PexType.STRING, PexType.INT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("promotePair()")
    class PromotePair {
        @Test void intAndInt() {
            var pair = NumericPromotion.promotePair(3, 4);
            assertThat(pair.resultType()).isEqualTo(PexType.INT);
            assertThat(pair.left().intValue()).isEqualTo(3);
            assertThat(pair.right().intValue()).isEqualTo(4);
        }

        @Test void intAndDouble() {
            var pair = NumericPromotion.promotePair(3, 4.5);
            assertThat(pair.resultType()).isEqualTo(PexType.DOUBLE);
            assertThat(pair.left().doubleValue()).isEqualTo(3.0);
            assertThat(pair.right().doubleValue()).isEqualTo(4.5);
        }

        @Test void longAndFloat() {
            var pair = NumericPromotion.promotePair(100L, 2.5f);
            assertThat(pair.resultType()).isEqualTo(PexType.DOUBLE);
        }
    }

    @Nested
    @DisplayName("isNumeric()")
    class IsNumeric {
        @Test void intIsNumeric() {
            assertThat(NumericPromotion.isNumeric(42)).isTrue();
        }

        @Test void longIsNumeric() {
            assertThat(NumericPromotion.isNumeric(42L)).isTrue();
        }

        @Test void floatIsNumeric() {
            assertThat(NumericPromotion.isNumeric(1.0f)).isTrue();
        }

        @Test void doubleIsNumeric() {
            assertThat(NumericPromotion.isNumeric(1.0)).isTrue();
        }

        @Test void stringIsNotNumeric() {
            assertThat(NumericPromotion.isNumeric("42")).isFalse();
        }

        @Test void nullIsNotNumeric() {
            assertThat(NumericPromotion.isNumeric(null)).isFalse();
        }
    }
}
