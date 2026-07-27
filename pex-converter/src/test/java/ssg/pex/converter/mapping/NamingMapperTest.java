package ssg.pex.converter.mapping;

import org.junit.jupiter.api.Test;
import ssg.pex.converter.ConversionConfig.NamingConvention;

import static org.assertj.core.api.Assertions.assertThat;

class NamingMapperTest {

    @Test
    void camelCaseFromSnakeCase() {
        assertThat(NamingMapper.toCamelCase("my_variable_name")).isEqualTo("myVariableName");
    }

    @Test
    void camelCaseFromPascalCase() {
        assertThat(NamingMapper.toCamelCase("MyVariableName")).isEqualTo("myVariableName");
    }

    @Test
    void camelCaseAlreadyCamelCase() {
        assertThat(NamingMapper.toCamelCase("myVariable")).isEqualTo("myVariable");
    }

    @Test
    void pascalCaseFromSnakeCase() {
        assertThat(NamingMapper.toPascalCase("my_variable_name")).isEqualTo("MyVariableName");
    }

    @Test
    void pascalCaseFromCamelCase() {
        assertThat(NamingMapper.toPascalCase("myVariableName")).isEqualTo("MyVariableName");
    }

    @Test
    void pascalCaseAlreadyPascalCase() {
        assertThat(NamingMapper.toPascalCase("MyVariable")).isEqualTo("MyVariable");
    }

    @Test
    void snakeCaseFromCamelCase() {
        assertThat(NamingMapper.toSnakeCase("myVariableName")).isEqualTo("my_variable_name");
    }

    @Test
    void snakeCaseFromPascalCase() {
        assertThat(NamingMapper.toSnakeCase("MyVariableName")).isEqualTo("my_variable_name");
    }

    @Test
    void snakeCaseAlreadySnakeCase() {
        assertThat(NamingMapper.toSnakeCase("my_variable")).isEqualTo("my_variable");
    }

    @Test
    void singleWord() {
        assertThat(NamingMapper.toCamelCase("hello")).isEqualTo("hello");
        assertThat(NamingMapper.toPascalCase("hello")).isEqualTo("Hello");
        assertThat(NamingMapper.toSnakeCase("hello")).isEqualTo("hello");
    }

    @Test
    void emptyString() {
        assertThat(NamingMapper.toCamelCase("")).isEqualTo("");
        assertThat(NamingMapper.toPascalCase("")).isEqualTo("");
        assertThat(NamingMapper.toSnakeCase("")).isEqualTo("");
    }

    @Test
    void convertWithPreserve() {
        assertThat(NamingMapper.convert("myVar", NamingConvention.PRESERVE)).isEqualTo("myVar");
    }

    @Test
    void convertWithCamelCase() {
        assertThat(NamingMapper.convert("MyVar", NamingConvention.CAMEL_CASE)).isEqualTo("myVar");
    }

    @Test
    void convertWithPascalCase() {
        assertThat(NamingMapper.convert("myVar", NamingConvention.PASCAL_CASE)).isEqualTo("MyVar");
    }

    @Test
    void convertWithSnakeCase() {
        assertThat(NamingMapper.convert("myVar", NamingConvention.SNAKE_CASE)).isEqualTo("my_var");
    }
}
